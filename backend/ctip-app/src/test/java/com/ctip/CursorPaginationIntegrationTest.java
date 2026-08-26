package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * cursor 分頁契約(docs/spec/09-api.md §9.3):連續翻頁至最後一頁,驗證無重複、無遺漏
 * (與 DB 上同一套可見性條件的 id 集合比對);limit 夾到上限;offset 上限 10000。
 * 專用 client IP,避免與其他測試共用匿名限流 bucket。
 */
@AutoConfigureMockMvc
class CursorPaginationIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String CLIENT_IP = "203.0.113.101";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void walksAllPagesWithoutDuplicatesOrGaps() throws Exception {
        Set<UUID> expected = new HashSet<>(jdbc.queryForList(
                "SELECT id FROM indicators i WHERE i.owner_tenant_id = '00000000-0000-0000-0000-000000000000'"
                        + " AND i.tlp = 'CLEAR' AND i.status <> 'EXPIRED' AND i.deleted_at IS NULL"
                        + " AND EXISTS (SELECT 1 FROM indicator_sources r WHERE r.indicator_id = i.id"
                        + " AND r.redistribution_policy <> 'INTERNAL_ONLY')",
                UUID.class));
        assertThat(expected).hasSizeGreaterThan(150); // 樣本資料保證多頁

        List<UUID> collected = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        while (true) {
            JsonNode page = getPage(cursor);
            page.get("items")
                    .forEach(
                            item -> collected.add(UUID.fromString(item.get("id").asString())));
            pages++;
            assertThat(pages).isLessThan(100); // 防呆:不因 cursor 錯誤而無限翻頁
            if (!page.get("hasMore").asBoolean()) {
                assertThat(page.get("nextCursor").isNull()).isTrue();
                break;
            }
            cursor = page.get("nextCursor").asString();
        }

        assertThat(pages).isGreaterThan(2);
        assertThat(collected).doesNotHaveDuplicates(); // 無重複
        assertThat(new HashSet<>(collected)).isEqualTo(expected); // 無遺漏
    }

    @Test
    void limitAboveMaxIsClampedNotRejected() throws Exception {
        JsonNode page = getPage(null, "/api/v1/iocs?limit=5000");
        assertThat(page.get("items").size()).isLessThanOrEqualTo(50);
    }

    @Test
    void offsetModeMatchesCursorOrderAndCapsAtTenThousand() throws Exception {
        JsonNode cursorPage = getPage(null, "/api/v1/iocs?limit=10");
        JsonNode offsetPage = getPage(null, "/api/v1/iocs?offset=5&limit=5");
        List<String> tail = new ArrayList<>();
        cursorPage.get("items").forEach(i -> tail.add(i.get("id").asString()));
        List<String> offsetIds = new ArrayList<>();
        offsetPage.get("items").forEach(i -> offsetIds.add(i.get("id").asString()));
        assertThat(offsetIds).isEqualTo(tail.subList(5, 10)); // 同一排序鍵下 offset 與 cursor 一致

        mvc.perform(request("/api/v1/iocs?offset=10001&limit=5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OFFSET_TOO_LARGE"));
    }

    @Test
    void unparsableCursorIsRejectedWithInvalidCursor() throws Exception {
        mvc.perform(request("/api/v1/iocs?cursor=%25%25not-base64url"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        mvc.perform(request("/api/v1/iocs?cursor="
                        + java.util.Base64.getUrlEncoder()
                                .encodeToString("{\"ls\":\"oops\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }

    private JsonNode getPage(String cursor) throws Exception {
        String url = "/api/v1/iocs?limit=50" + (cursor == null ? "" : "&cursor=" + cursor);
        return getPage(cursor, url);
    }

    private JsonNode getPage(String cursorIgnored, String url) throws Exception {
        MvcResult result = mvc.perform(request(url)).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static MockHttpServletRequestBuilder request(String url) {
        return get(url).with(req -> {
            req.setRemoteAddr(CLIENT_IP);
            return req;
        });
    }
}
