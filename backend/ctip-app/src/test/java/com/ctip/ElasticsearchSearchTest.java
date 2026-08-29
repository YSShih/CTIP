package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.IndicatorRepository;
import com.ctip.application.port.SourceRepository;
import com.ctip.application.search.SearchReconciliationService;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.SourceType;
import com.ctip.sdk.Tlp;
import com.ctip.support.BloomFixtures;
import com.ctip.support.BloomTenants;
import com.ctip.support.ElasticsearchTestContainer;
import com.ctip.support.IndicatorFixtures;
import com.ctip.support.SearchIndexControl;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * DoD M2-22:Elasticsearch 索引建立、搜尋正確(docs/spec/13-platform-ops.md §13.7)。
 * L4(heavy):真的起一個 {@code elasticsearch:9.5.1} 容器(14 §14.1)。
 *
 * <p>本測試量的不只是「查得到」。§13.7 的搜尋欄位清單<strong>不含</strong> {@code ownerTenantId}、
 * {@code deletedAt} 與來源的再散布政策,而那三者是可見度與側信道防護的全部依據(ADR 0015、ADR 0020 §8)
 * ——照字面實作,ES 路徑會整套繞過過濾。因此可見度的三個案例與「索引被污染」的案例是本測試的重點。
 */
@Tag("heavy")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureMockMvc
@SpringBootTest(properties = "ctip.search.backend=elasticsearch")
class ElasticsearchSearchTest extends AbstractPostgresIntegrationTest {

    private static final String CLIENT_IP = "10.60.0.11";
    private static final String VISIBLE = "esvis-public-clear";
    private static final String PRIVATE = "esvis-tenant-amber";
    private static final String INTERNAL = "esvis-public-internal";
    private static final String GREEN = "esvis-public-green";
    private static final String DOMAIN_SUFFIX = ".security.ctip-sample.net";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ElasticsearchClient client;

    @Autowired
    private SearchReconciliationService reconciliation;

    @Autowired
    private IndicatorRepository indicators;

    @Autowired
    private SourceRepository sources;

    @Autowired
    private com.ctip.application.port.TenantRepository tenantRepository;

    @Autowired
    private com.ctip.application.port.SubscriptionRepository subscriptionRepository;

    @Autowired
    private com.ctip.application.port.PlanRepository planRepository;

    @Autowired
    private IdGeneratorPort idGenerator;

    @Autowired
    private com.ctip.application.port.ClockPort clock;

    private SearchIndexControl index;
    private TenantId otherTenant;

    @DynamicPropertySource
    static void elasticsearch(DynamicPropertyRegistry registry) {
        // 走 §5.7 的環境變數對應(ELASTICSEARCH_URL → spring.elasticsearch.uris),連這條也一起驗
        registry.add("ELASTICSEARCH_URL", ElasticsearchTestContainer::url);
    }

    @BeforeAll
    void seedAndIndex() {
        BloomTenants tenants =
                new BloomTenants(tenantRepository, subscriptionRepository, planRepository, idGenerator, clock);
        otherTenant = tenants.create("es-search-tenant");
        SourceId sourceId = sources.findBySourceType(SourceType.MOCK_OPENPHISH)
                .orElseThrow()
                .id();
        upsert(
                sourceId,
                new IndicatorFixtures.Fixture(
                        BloomFixtures.id("0000e5a1"),
                        TenantId.PUBLIC,
                        Tlp.CLEAR,
                        RedistributionPolicy.PUBLIC_REDISTRIBUTABLE,
                        VISIBLE));
        upsert(
                sourceId,
                new IndicatorFixtures.Fixture(
                        BloomFixtures.id("0000e5a2"),
                        otherTenant,
                        Tlp.AMBER,
                        RedistributionPolicy.INTERNAL_ONLY,
                        PRIVATE));
        upsert(
                sourceId,
                new IndicatorFixtures.Fixture(
                        BloomFixtures.id("0000e5a3"),
                        TenantId.PUBLIC,
                        Tlp.CLEAR,
                        RedistributionPolicy.INTERNAL_ONLY,
                        INTERNAL));
        upsert(
                sourceId,
                new IndicatorFixtures.Fixture(
                        BloomFixtures.id("0000e5a4"),
                        TenantId.PUBLIC,
                        Tlp.GREEN,
                        RedistributionPolicy.PUBLIC_REDISTRIBUTABLE,
                        GREEN));
        index = new SearchIndexControl(client, reconciliation);
        index.rebuild();
    }

    private void upsert(SourceId sourceId, IndicatorFixtures.Fixture fixture) {
        IndicatorFixtures.upsert(indicators, sourceId, fixture);
    }

    @Test
    void indexIsBuiltFromTheDatabaseAndServesTheQuery() throws Exception {
        assertThat(index.count()).isPositive();
        MvcResult result = search("{\"query\":\"" + VISIBLE + "\"}");
        assertThat(values(result)).contains(VISIBLE + DOMAIN_SUFFIX);
        assertThat(result.getResponse().getHeader("X-Search-Backend")).isEqualTo("elasticsearch");
    }

    @Test
    void filtersAndCursorPaginationBehaveLikeTheDatabasePath() throws Exception {
        JsonNode first = body(search("{\"query\":\"ctip-sample\",\"type\":\"DOMAIN\",\"limit\":5}"));
        assertThat(first.get("items").size()).isEqualTo(5);
        assertThat(first.get("hasMore").asBoolean()).isTrue();
        first.get("items")
                .forEach(item -> assertThat(item.get("type").asString()).isEqualTo("DOMAIN"));

        JsonNode second = body(search("{\"query\":\"ctip-sample\",\"type\":\"DOMAIN\",\"limit\":5,\"cursor\":\""
                + first.get("nextCursor").asString() + "\"}"));
        List<String> firstIds = new ArrayList<>();
        first.get("items").forEach(item -> firstIds.add(item.get("id").asString()));
        second.get("items")
                .forEach(item ->
                        assertThat(firstIds).doesNotContain(item.get("id").asString()));
    }

    /** §13.7:模糊查詢(僅 M2)用於 typosquatting 偵測。關閉時同一個查詢必須查無,否則證明不了是 fuzzy 起的作用。 */
    @Test
    void fuzzyMatchingFindsTyposquattedValues() throws Exception {
        String typo = "esvis-public-clbar" + DOMAIN_SUFFIX;
        assertThat(values(search("{\"query\":\"" + typo + "\"}"))).isEmpty();
        assertThat(values(search("{\"query\":\"" + typo + "\",\"fuzzy\":true}")))
                .contains(VISIBLE + DOMAIN_SUFFIX);
    }

    /**
     * 對匿名不可見的三種情形各一筆:他租戶的 AMBER(租戶範圍)、public 的 `TLP:GREEN`
     * (匿名的 `maxPublicTlp` 是 CLEAR)、以及只有 `INTERNAL_ONLY` 來源的公開情資
     * (I14 / §7.9 規則 3)。四筆 fixture 只有一筆該回來。
     */
    @Test
    void visibilityPredicatesAreRebuiltOnTheIndex() throws Exception {
        List<String> visible = values(search("{\"query\":\"esvis-\",\"limit\":50}"));
        assertThat(visible).containsExactly(VISIBLE + DOMAIN_SUFFIX);
    }

    /**
     * 可見度必須在 <strong>ES 端</strong>就成立,而不只靠回傳前的資料庫再過濾。
     *
     * <p>四筆 {@code esvis-} fixture 依 {@code (lastSeen, id) DESC} 的順序是
     * 不可見、不可見、不可見、可見。若 ES 的述詞漏了,前兩筆會佔滿這一頁、被資料庫過濾成空,
     * 呼叫端拿到「0 筆但 hasMore=true」——分頁壞掉,而且「本頁少了幾筆」本身就是側信道。
     */
    @Test
    void invisibleDocumentsDoNotConsumeThePage() throws Exception {
        JsonNode page = body(search("{\"query\":\"esvis-\",\"limit\":2}"));
        assertThat(page.get("items").size()).isEqualTo(1);
        assertThat(page.get("hasMore").asBoolean()).isFalse();
    }

    /**
     * 索引被污染時仍不得洩漏:直接把一筆他租戶 AMBER 的 indicator 以「public + 可再散布」的形狀
     * 寫進索引,ES 會命中它,但回傳前一律由 PostgreSQL 依可見度取回——因此它出不去。
     */
    @Test
    void poisonedIndexDocumentsStillCannotEscapeVisibility() throws Exception {
        String hidden = BloomFixtures.id("0000e5a2").value().toString();
        index.poison(hidden, poisonedSource(hidden));
        try {
            assertThat(values(search("{\"query\":\"" + PRIVATE + "\",\"limit\":50}")))
                    .doesNotContain(PRIVATE + DOMAIN_SUFFIX);
        } finally {
            index.rebuild();
        }
    }

    private Map<String, Object> poisonedSource(String id) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("id", id);
        source.put("ownerTenantId", TenantId.PUBLIC.value().toString());
        source.put("value", PRIVATE + DOMAIN_SUFFIX);
        source.put("normalizedValue", PRIVATE + DOMAIN_SUFFIX);
        source.put("type", "DOMAIN");
        source.put("severity", "MEDIUM");
        source.put("status", "ACTIVE");
        source.put("tlp", "CLEAR");
        source.put("tags", List.of("security-test"));
        source.put("confidence", 60);
        source.put("score", 50);
        source.put("firstSeen", IndicatorFixtures.SEEN.toString());
        source.put("lastSeen", IndicatorFixtures.SEEN.toString());
        source.put("validUntil", null);
        source.put("lastSeenNanos", IndicatorFixtures.SEEN.getEpochSecond() * 1_000_000_000L);
        source.put("redistributable", true);
        source.put("sourceIds", List.of());
        source.put("disclosableSourceIds", List.of());
        source.put("updatedAtNanos", Instant.now().getEpochSecond() * 1_000_000_000L);
        return source;
    }

    private MvcResult search(String json) throws Exception {
        return mvc.perform(post("/api/v1/iocs/search")
                        .with(request -> {
                            request.setRemoteAddr(CLIENT_IP);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Search-Backend", "elasticsearch"))
                .andReturn();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private List<String> values(MvcResult result) throws Exception {
        List<String> values = new ArrayList<>();
        body(result).get("items").forEach(item -> values.add(item.get("value").asString()));
        return values;
    }
}
