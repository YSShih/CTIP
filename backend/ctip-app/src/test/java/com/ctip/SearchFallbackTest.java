package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctip.application.port.SearchPort;
import com.ctip.infrastructure.search.FallbackSearchAdapter;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
 * DoD M2-23:ES 掛掉時 API 降級為 PostgreSQL——回 <strong>200</strong>(非 500)且帶
 * {@code X-Search-Backend: postgres}(docs/spec/13-platform-ops.md §13.7、phase-19 完成判準)。
 *
 * <p>「ES 停止」以一個沒有任何東西在聽的位址表達:連線被拒是 ES 不可用最乾淨的形式,
 * 而且不需要為了停掉一個容器而把測試變成 heavy。應用<strong>確實</strong>裝配了 ES 後端
 * ({@code SEARCH_BACKEND=elasticsearch}),下方第一個測試明確斷言這件事——
 * 否則「永遠回 postgres」會是一個假綠。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureMockMvc
@SpringBootTest(properties = "ctip.search.backend=elasticsearch")
class SearchFallbackTest extends AbstractPostgresIntegrationTest {

    private static final String CLIENT_IP = "10.60.0.12";

    /** 保留埠 1:不會有任何服務在聽,連線立即被拒。 */
    private static final String DEAD_ELASTICSEARCH = "http://127.0.0.1:1";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SearchPort searchPort;

    @DynamicPropertySource
    static void elasticsearch(DynamicPropertyRegistry registry) {
        registry.add("ELASTICSEARCH_URL", () -> DEAD_ELASTICSEARCH);
    }

    @Test
    void theElasticsearchPathIsActuallyWiredUp() {
        assertThat(searchPort)
                .as("注入的 SearchPort 必須是組合實作,降級才可能發生(ADR 0020 §8 的 bean 歧義)")
                .isInstanceOf(FallbackSearchAdapter.class);
    }

    @Test
    void searchDegradesToPostgresWithTwoHundredAndTheBackendHeader() throws Exception {
        MvcResult result = mvc.perform(request("{\"query\":\"mal-8.ctip-sample\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Search-Backend", "postgres"))
                .andReturn();
        assertThat(values(result)).contains("mal-8.ctip-sample.net");
    }

    /** 降級不得只是「不報錯」:過濾條件與分頁在 PostgreSQL 路徑上仍必須完整生效。 */
    @Test
    void degradedSearchStillAppliesFiltersAndPagination() throws Exception {
        MvcResult result = mvc.perform(request("{\"query\":\"ctip-sample\",\"type\":\"DOMAIN\",\"limit\":5}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Search-Backend", "postgres"))
                .andReturn();
        JsonNode page = body(result);
        assertThat(page.get("items").size()).isEqualTo(5);
        page.get("items")
                .forEach(item -> assertThat(item.get("type").asString()).isEqualTo("DOMAIN"));
        assertThat(page.get("hasMore").asBoolean()).isTrue();
    }

    /**
     * 斷路器必須真的開路。沒有它,ES 掛掉時每一次查詢都要先等一次連線失敗——
     * 「降級成功」會伴隨每個請求的額外延遲,對使用者而言服務仍然是壞的。
     */
    @Test
    void theCircuitBreakerOpensSoDegradedQueriesStopPayingForElasticsearch() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(request("{\"query\":\"ctip-sample\",\"limit\":1}")).andExpect(status().isOk());
        }
        assertThat(((FallbackSearchAdapter) searchPort).circuitBreakerState())
                .isIn(CircuitBreaker.State.OPEN, CircuitBreaker.State.FORCED_OPEN);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String json) {
        return post("/api/v1/iocs/search")
                .with(servletRequest -> {
                    servletRequest.setRemoteAddr(CLIENT_IP);
                    return servletRequest;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
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
