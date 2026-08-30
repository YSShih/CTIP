package com.ctip.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.support.LoggingFormats;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** 結構化 JSON 日誌的九個必含欄位(docs/spec/13-platform-ops.md §13.6)。 */
@Tag("unit")
class CtipJsonEncoderTest {

    private static final Map<String, String> CONTEXT = Map.of(
            "traceId", "0af7651916cd43dd8448eb211c80319c",
            "spanId", "b7ad6b7169203331",
            "requestId", "req-1",
            "tenantId", "00000000-0000-0000-0000-000000000001",
            "userId", "00000000-0000-0000-0000-000000000002");

    static java.util.stream.Stream<String> requiredFields() {
        return LogFields.REQUIRED.stream();
    }

    @ParameterizedTest
    @MethodSource("requiredFields")
    void everyRequiredFieldIsPresent(String field) {
        assertThat(LoggingFormats.encodeAsJson("hello", CONTEXT)).contains("\"" + field + "\"");
    }

    /** 缺欄位與空值在下游查詢是兩件事:MDC 沒有值時仍要輸出欄位。 */
    @Test
    void theCorrelationFieldsAreEmittedEvenWithoutMdc() {
        String json = LoggingFormats.encodeAsJson("hello", Map.of());

        LogFields.MDC_FIELDS.forEach(field -> assertThat(json).contains("\"" + field + "\":\"\""));
    }

    @Test
    void theMdcValuesAreCarriedThrough() {
        String json = LoggingFormats.encodeAsJson("hello", CONTEXT);

        assertThat(json).contains("\"traceId\":\"0af7651916cd43dd8448eb211c80319c\"");
        assertThat(json).contains("\"service\":\"ctip\"");
        assertThat(json).contains("\"environment\":\"mvp\"");
    }
}
