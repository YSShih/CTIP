package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * POST /iocs/search 與 POST /iocs/lookup(docs/spec/09-api.md §9.1;M1 = PostgreSQL 搜尋),
 * 以及輸出過濾第 4 步的再散布遮罩(07 §7.9 規則 4/5)。
 * 樣本資料:mal-8(來源 ATTRIBUTION_REQUIRED)、mal-20(來源 DERIVED_ONLY),皆 public CLEAR ACTIVE。
 */
@AutoConfigureMockMvc
class IocSearchIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String CLIENT_IP = "203.0.113.102";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void searchFindsByValueSubstringWithinVisibility() throws Exception {
        JsonNode page = postJson("/api/v1/iocs/search", "{\"query\":\"mal-8.ctip-sample\"}");
        List<String> values = new ArrayList<>();
        page.get("items").forEach(item -> values.add(item.get("value").asString()));
        assertThat(values).contains("mal-8.ctip-sample.net");
        page.get("items").forEach(item -> assertThat(item.get("tlp").asString()).isEqualTo("CLEAR")); // 匿名可見度
    }

    @Test
    void searchAppliesTypeFilterAndPaginates() throws Exception {
        JsonNode first = postJson("/api/v1/iocs/search", "{\"query\":\"ctip-sample\",\"type\":\"DOMAIN\",\"limit\":5}");
        assertThat(first.get("items").size()).isEqualTo(5);
        assertThat(first.get("hasMore").asBoolean()).isTrue();
        first.get("items")
                .forEach(item -> assertThat(item.get("type").asString()).isEqualTo("DOMAIN"));

        JsonNode second = postJson(
                "/api/v1/iocs/search",
                "{\"query\":\"ctip-sample\",\"type\":\"DOMAIN\",\"limit\":5,\"cursor\":\""
                        + first.get("nextCursor").asString() + "\"}");
        List<String> firstIds = new ArrayList<>();
        first.get("items").forEach(i -> firstIds.add(i.get("id").asString()));
        second.get("items")
                .forEach(i -> assertThat(firstIds).doesNotContain(i.get("id").asString()));
    }

    @Test
    void searchLikeWildcardsAreLiteral() throws Exception {
        // % 若未跳脫會變成 match-all;跳脫後應查無
        JsonNode page = postJson("/api/v1/iocs/search", "{\"query\":\"%%%\"}");
        assertThat(page.get("items").size()).isZero();
    }

    @Test
    void listFiltersByTagsScoreAndTimeRange() throws Exception {
        // tags 為全部包含(@>,GIN):sample+phishing 命中 i%3==0 的樣本
        JsonNode page = getJson("/api/v1/iocs?tags=sample&tags=phishing&scoreMin=40&scoreMax=90&limit=50");
        assertThat(page.get("items").size()).isGreaterThan(0);
        page.get("items").forEach(item -> {
            List<String> tags = new ArrayList<>();
            item.get("tags").forEach(tag -> tags.add(tag.asString()));
            assertThat(tags).contains("sample", "phishing");
            assertThat(item.get("score").asInt()).isBetween(40, 90);
        });

        // 不存在的 tag 組合必須查無(而非忽略條件)
        JsonNode none = getJson("/api/v1/iocs?tags=sample&tags=no-such-tag&limit=5");
        assertThat(none.get("items").size()).isZero();

        // lastSeen 閉區間
        java.time.Instant to = java.time.Instant.now();
        java.time.Instant from = to.minus(java.time.Duration.ofDays(30));
        JsonNode ranged = getJson("/api/v1/iocs?lastSeenFrom=" + from + "&lastSeenTo=" + to + "&limit=50");
        assertThat(ranged.get("items").size()).isGreaterThan(0);
        ranged.get("items").forEach(item -> {
            java.time.Instant lastSeen =
                    java.time.Instant.parse(item.get("lastSeen").asString());
            assertThat(lastSeen).isBetween(from, to);
        });
    }

    @Test
    void searchFiltersBySourceIdAndConfidenceRange() throws Exception {
        UUID openphish = jdbc.queryForObject("SELECT id FROM sources WHERE source_type = 'MOCK_OPENPHISH'", UUID.class);
        JsonNode page = postJson(
                "/api/v1/iocs/search",
                "{\"query\":\"ctip-sample\",\"sourceId\":\"" + openphish
                        + "\",\"confidenceMin\":10,\"confidenceMax\":95,\"limit\":20}");
        assertThat(page.get("items").size()).isGreaterThan(0);
        page.get("items").forEach(item -> {
            assertThat(item.get("confidence").asInt()).isBetween(10, 95);
            Integer reported = jdbc.queryForObject(
                    "SELECT count(*) FROM indicator_sources WHERE indicator_id = ?::uuid AND source_id = ?",
                    Integer.class,
                    item.get("id").asString(),
                    openphish);
            assertThat(reported).isPositive();
        });
    }

    @Test
    void lookupVerifiesExactValuesAfterNormalization() throws Exception {
        JsonNode response = postJson(
                "/api/v1/iocs/lookup",
                "{\"values\":[\"MAL-8.CTIP-SAMPLE.NET.\",\"phisher5@mal-5.ctip-sample.net\","
                        + "\"no-such.ctip-sample.net\",\"!!!\"]}");
        JsonNode results = response.get("results");
        assertThat(results.get(0).get("found").asBoolean()).isTrue(); // 大小寫+尾端點正規化後命中
        assertThat(results.get(0).get("ioc").get("value").asString()).isEqualTo("mal-8.ctip-sample.net");
        assertThat(results.get(1).get("found").asBoolean()).isFalse(); // GREEN:匿名不可見 → 未命中
        assertThat(results.get(2).get("found").asBoolean()).isFalse();
        assertThat(results.get(3).get("found").asBoolean()).isFalse(); // 無法推斷型別 → 未命中非錯誤
    }

    @Test
    void lookupBatchOverLimitIsRejectedWithPayloadTooLarge() throws Exception {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            values.add("v" + i + ".ctip-sample.net");
        }
        mvc.perform(request(post("/api/v1/iocs/lookup"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"values\":" + objectMapper.writeValueAsString(values) + "}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void attributionRequiredSourceYieldsAttributionAndSourceDetail() throws Exception {
        UUID id = idOf("mal-8.ctip-sample.net"); // 來源 Mock OpenPhish(ATTRIBUTION_REQUIRED)
        JsonNode detail = getJson("/api/v1/iocs/" + id);
        assertThat(detail.get("attribution").size()).isEqualTo(1);
        assertThat(detail.get("attribution").get(0).get("sourceName").asString())
                .isEqualTo("Mock OpenPhish");

        JsonNode sources = getJson("/api/v1/iocs/" + id + "/sources");
        assertThat(sources.size()).isEqualTo(1);
        assertThat(sources.get(0).get("sourceName").asString()).isEqualTo("Mock OpenPhish");
    }

    @Test
    void derivedOnlySourceIsMaskedFromSourceDetailButSummaryRemains() throws Exception {
        UUID id = idOf("mal-20.ctip-sample.net"); // 來源 Mock AbuseIPDB(DERIVED_ONLY)
        JsonNode detail = getJson("/api/v1/iocs/" + id);
        // 規則 5:可回答風險評估欄位,不得含來源明細;attribution 為空
        assertThat(detail.get("score").isNumber()).isTrue();
        assertThat(detail.get("severity").asString()).isNotEmpty();
        assertThat(detail.get("attribution").size()).isZero();

        JsonNode sources = getJson("/api/v1/iocs/" + id + "/sources");
        assertThat(sources.size()).isZero();
    }

    /**
     * 迴歸鎖(ADR 0015):遮蔽了來源明細,就不能讓 {@code sourceId} 過濾當 oracle 用。
     *
     * <p>原本查詢述詞比對任何政策的來源記錄,而輸出過濾遮蔽 DERIVED_ONLY ——
     * 逐一試 {@code ?sourceId=} 就能還原被遮蔽的來源歸屬。兩者現在用同一條揭露規則。
     */
    @Test
    void filteringByAMaskedSourceLeaksNothingToAnonymous() throws Exception {
        UUID derivedOnly =
                jdbc.queryForObject("SELECT id FROM sources WHERE source_type = 'MOCK_ABUSEIPDB'", UUID.class);
        // 先確認測試不是空轉:該來源在資料庫裡確實有記錄
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM indicator_sources WHERE source_id = ?"
                                + " AND redistribution_policy = 'DERIVED_ONLY'",
                        Integer.class,
                        derivedOnly))
                .isPositive();

        JsonNode masked = postJson(
                "/api/v1/iocs/search", "{\"query\":\"ctip-sample\",\"sourceId\":\"" + derivedOnly + "\",\"limit\":20}");
        assertThat(masked.get("items").size())
                .as("DERIVED_ONLY 來源的歸屬既然不對匿名輸出,就不得經 sourceId 過濾被還原")
                .isZero();

        // 對照組:ATTRIBUTION_REQUIRED 的來源仍可正常過濾(§13.7 的搜尋能力未被削掉)
        UUID attributed =
                jdbc.queryForObject("SELECT id FROM sources WHERE source_type = 'MOCK_OPENPHISH'", UUID.class);
        JsonNode visible = postJson(
                "/api/v1/iocs/search", "{\"query\":\"ctip-sample\",\"sourceId\":\"" + attributed + "\",\"limit\":20}");
        assertThat(visible.get("items").size()).isPositive();
    }

    private UUID idOf(String normalizedValue) {
        return jdbc.queryForObject("SELECT id FROM indicators WHERE normalized_value = ?", UUID.class, normalizedValue);
    }

    private JsonNode postJson(String url, String body) throws Exception {
        MvcResult result = mvc.perform(request(post(url))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getJson(String url) throws Exception {
        MvcResult result =
                mvc.perform(request(get(url))).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static MockHttpServletRequestBuilder request(MockHttpServletRequestBuilder builder) {
        return builder.with(req -> {
            req.setRemoteAddr(CLIENT_IP);
            return req;
        });
    }
}
