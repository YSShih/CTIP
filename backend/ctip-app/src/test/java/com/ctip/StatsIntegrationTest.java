package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * GET /stats/summary 與 /stats/sources(docs/spec/09-api.md §9.1;Phase 12 DashboardPage 的資料來源)。
 * 驗證:summary 的匿名可見度口徑(public CLEAR ACTIVE)、byType 總和一致性、
 * 近 7 日趨勢補 0 與 UTC 日界,以及 sources 的各來源筆數。
 */
@AutoConfigureMockMvc
class StatsIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String CLIENT_IP = "203.0.113.150";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void summaryCountsOnlyAnonymouslyVisibleActiveIndicators() throws Exception {
        JsonNode summary = getJson("/api/v1/stats/summary");
        // 期望值 SQL 必須與可見度規則完整同步(含再散布條件),否則 public+CLEAR+INTERNAL_ONLY
        // 的樣本(SecurityTest fixture)會使期望值虛高
        Long expected = jdbc.queryForObject(
                "SELECT count(*) FROM indicators i WHERE i.owner_tenant_id = '00000000-0000-0000-0000-000000000000'"
                        + " AND i.tlp = 'CLEAR' AND i.status = 'ACTIVE'"
                        + " AND EXISTS (SELECT 1 FROM indicator_sources r WHERE r.indicator_id = i.id"
                        + " AND r.redistribution_policy <> 'INTERNAL_ONLY')",
                Long.class);
        assertThat(summary.get("totalActive").asLong()).isEqualTo(expected);

        long byTypeSum = 0;
        var fields = summary.get("byType").properties().iterator();
        while (fields.hasNext()) {
            byTypeSum += fields.next().getValue().asLong();
        }
        assertThat(byTypeSum).isEqualTo(summary.get("totalActive").asLong());
    }

    @Test
    void summaryTrendCoversSevenUtcDaysWithZeroFill() throws Exception {
        JsonNode summary = getJson("/api/v1/stats/summary");
        JsonNode trend = summary.get("trend");
        assertThat(trend.size()).isEqualTo(7);

        List<LocalDate> dates = new ArrayList<>();
        long trendSum = 0;
        for (JsonNode day : trend) {
            dates.add(LocalDate.parse(day.get("date").asString()));
            assertThat(day.get("count").asLong()).isGreaterThanOrEqualTo(0);
            trendSum += day.get("count").asLong();
        }
        for (int i = 1; i < dates.size(); i++) {
            assertThat(dates.get(i)).isEqualTo(dates.get(i - 1).plusDays(1)); // 連續、含補 0 的日期
        }
        assertThat(dates.getLast()).isEqualTo(LocalDate.now(ZoneOffset.UTC));

        Instant windowStart = Instant.now().minus(Duration.ofDays(6)).truncatedTo(ChronoUnit.DAYS);
        Long expected = jdbc.queryForObject(
                "SELECT count(*) FROM indicators i WHERE i.owner_tenant_id = '00000000-0000-0000-0000-000000000000'"
                        + " AND i.tlp = 'CLEAR' AND i.status = 'ACTIVE' AND i.last_seen >= ?"
                        + " AND EXISTS (SELECT 1 FROM indicator_sources r WHERE r.indicator_id = i.id"
                        + " AND r.redistribution_policy <> 'INTERNAL_ONLY')",
                Long.class,
                java.sql.Timestamp.from(windowStart));
        assertThat(trendSum).isEqualTo(expected);
    }

    @Test
    void sourcesReportPerSourceCountsAndHealth() throws Exception {
        JsonNode sources = getJson("/api/v1/stats/sources");
        List<String> types = new ArrayList<>();
        sources.forEach(source -> types.add(source.get("sourceType").asString()));
        assertThat(types).contains("MANUAL", "MOCK_OPENPHISH", "MOCK_ABUSEIPDB", "MOCK_ALIENVAULT");

        for (JsonNode source : sources) {
            assertThat(source.get("displayName").asString()).isNotEmpty();
            assertThat(source.get("status").asString()).isNotEmpty();
            long count = source.get("indicatorCount").asLong();
            Long expected = jdbc.queryForObject(
                    "SELECT count(*) FROM indicator_sources WHERE source_id = ?::uuid",
                    Long.class,
                    source.get("sourceId").asString());
            assertThat(count).isEqualTo(expected);
            if (source.get("sourceType").asString().startsWith("MOCK_")) {
                assertThat(count).isPositive(); // 種子每筆樣本 IOC 都掛一個 mock 來源
            }
        }
    }

    private JsonNode getJson(String url) throws Exception {
        MvcResult result = mvc.perform(get(url).with(req -> {
                    req.setRemoteAddr(CLIENT_IP);
                    return req;
                }))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
