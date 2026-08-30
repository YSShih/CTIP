package com.ctip.infrastructure.observability;

import java.util.List;

/**
 * 結構化日誌的必含欄位(docs/spec/13-platform-ops.md §13.6):
 * {@code timestamp}、{@code level}、{@code service}、{@code environment}、{@code traceId}、
 * {@code spanId}、{@code requestId}、{@code tenantId}、{@code userId}。
 *
 * <p>其中五個是 MDC 鍵:{@code traceId} / {@code spanId} 由追蹤(OpenTelemetry 橋接)寫入,
 * {@code requestId} 由 {@link TraceIdFilter} 寫入,{@code tenantId} / {@code userId}
 * 由 {@link LoggingContextFilter} 在認證之後寫入。
 */
public final class LogFields {

    public static final String TIMESTAMP = "timestamp";
    public static final String LEVEL = "level";
    public static final String SERVICE = "service";
    public static final String ENVIRONMENT = "environment";
    public static final String TRACE_ID = "traceId";
    public static final String SPAN_ID = "spanId";
    public static final String REQUEST_ID = "requestId";
    public static final String TENANT_ID = "tenantId";
    public static final String USER_ID = "userId";

    /** 從 MDC 取值的五個欄位,依 §13.6 的順序。 */
    public static final List<String> MDC_FIELDS = List.of(TRACE_ID, SPAN_ID, REQUEST_ID, TENANT_ID, USER_ID);

    /** 九個必含欄位。 */
    public static final List<String> REQUIRED =
            List.of(TIMESTAMP, LEVEL, SERVICE, ENVIRONMENT, TRACE_ID, SPAN_ID, REQUEST_ID, TENANT_ID, USER_ID);

    private LogFields() {}
}
