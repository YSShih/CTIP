package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctip.domain.tenant.TenantId;
import com.ctip.infrastructure.observability.LogFields;
import com.ctip.infrastructure.observability.TraceIdFilter;
import com.ctip.support.LogCapture;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * DoD M3-14(追蹤鏈 API → service → DB)與 M3-16(traceId 同時出現在錯誤回應與日誌),
 * 治理規格 docs/spec/13-platform-ops.md §13.6、09 §9.4。
 *
 * <p>span 由一個測試用的 {@code SpanProcessor} 收集——Boot 會把 context 中所有
 * {@code SpanProcessor} bean 併入 {@code SdkTracerProvider},因此收到的就是正式路徑上的 span。
 */
@AutoConfigureMockMvc
@Import(TracePropagationTest.RecordedSpansConfig.class)
class TracePropagationTest extends AbstractPostgresIntegrationTest {

    private static final String CLIENT_IP = "10.100.0.13";
    private static final String INCOMING_TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
    private static final String TRACEPARENT = "00-" + INCOMING_TRACE_ID + "-b7ad6b7169203331-01";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordedSpans spans;

    /** M3-14:一次 API 請求的 trace 必須一路串到 application service 與資料庫存取。 */
    @Test
    void oneRequestProducesOneTraceCoveringTheApiServiceAndDatabase() throws Exception {
        spans.clear();

        mvc.perform(asClient(get("/api/v1/iocs?limit=1")).header("traceparent", TRACEPARENT))
                .andExpect(status().isOk());

        List<String> names = spans.namesOf(INCOMING_TRACE_ID);
        assertThat(names).as("傳入的 traceparent 必須被延續,而不是另開一個 trace").isNotEmpty();
        assertThat(names).anySatisfy(name -> assertThat(name).contains("Service#"));
        assertThat(names).anySatisfy(name -> assertThat(name).contains("Adapter#"));
    }

    /** M3-16:錯誤回應上的 traceId 與同一次請求日誌中的 traceId 必須是同一個值。 */
    @Test
    void theTraceIdOnTheErrorResponseIsTheOneCarriedByTheLogs() throws Exception {
        try (LogCapture logs = LogCapture.start()) {
            MvcResult result = mvc.perform(asClient(get("/api/v1/iocs/00000000-0000-0000-0000-0000000000ff"))
                            .header("traceparent", TRACEPARENT))
                    .andExpect(status().isNotFound())
                    .andReturn();

            assertThat(result.getResponse().getContentAsString()).contains("\"traceId\":\"" + INCOMING_TRACE_ID + "\"");
            assertThat(logs.mdcValues("traceId")).contains(INCOMING_TRACE_ID);
        }
    }

    /**
     * 九個必含欄位中的 {@code tenantId} / {@code userId} 由認證之後的 filter 放進 MDC——
     * 驗它們真的出現在請求期間的日誌上,而不只是編碼器能輸出這兩個欄位名。
     */
    @Test
    void logsEmittedDuringARequestCarryTheTenant() throws Exception {
        try (LogCapture logs = LogCapture.start()) {
            mvc.perform(asClient(get("/api/v1/iocs?limit=1"))).andExpect(status().isOk());

            assertThat(logs.mdcValues(LogFields.TENANT_ID))
                    .as("請求期間的日誌必須帶得出租戶(匿名綁 public tenant)")
                    .contains(TenantId.PUBLIC.value().toString());
        }
    }

    /** 沒有 traceparent 時仍要有可關聯的識別碼:traceId(32 hex)與 requestId。 */
    @Test
    void aRequestWithoutTraceparentStillGetsCorrelationIdentifiers() throws Exception {
        MvcResult result = mvc.perform(asClient(get("/api/v1/iocs/00000000-0000-0000-0000-0000000000ff")))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).containsPattern("\"traceId\":\"[0-9a-f]{32}\"");
        assertThat(result.getResponse().getHeader(TraceIdFilter.REQUEST_ID_HEADER))
                .isNotBlank();
    }

    private static MockHttpServletRequestBuilder asClient(MockHttpServletRequestBuilder builder) {
        return builder.with(request -> {
            request.setRemoteAddr(CLIENT_IP);
            return request;
        });
    }

    @TestConfiguration
    static class RecordedSpansConfig {

        @Bean
        RecordedSpans recordedSpans() {
            return new RecordedSpans();
        }
    }

    /** 收集已結束的 span(名稱 + traceId)。 */
    static final class RecordedSpans implements SpanProcessor {

        private final List<String[]> ended = new CopyOnWriteArrayList<>();

        void clear() {
            ended.clear();
        }

        List<String> namesOf(String traceId) {
            return ended.stream()
                    .filter(entry -> entry[0].equals(traceId))
                    .map(entry -> entry[1])
                    .toList();
        }

        @Override
        public void onStart(Context parentContext, ReadWriteSpan span) {
            // 只在結束時記錄:名稱在 span 結束前才定案
        }

        @Override
        public boolean isStartRequired() {
            return false;
        }

        @Override
        public void onEnd(ReadableSpan span) {
            ended.add(new String[] {span.getSpanContext().getTraceId(), span.getName()});
        }

        @Override
        public boolean isEndRequired() {
            return true;
        }
    }
}
