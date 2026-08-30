package com.ctip.infrastructure.observability;

import com.ctip.infrastructure.web.FilterErrorWriter;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 每請求 traceId(docs/spec/09-api.md §9.4:錯誤回應的 traceId 必須與日誌可對應)
 * 與 requestId(13 §13.6 的九個必含欄位之一)。
 *
 * <p><strong>traceId 的真相來源是 OpenTelemetry</strong>(Phase 22 起):本 filter 排在
 * Boot 的 {@code ServerHttpObservationFilter}(order = HIGHEST_PRECEDENCE + 1)<strong>之後</strong>,
 * 因此進來時 server span 已經建立,直接取它的 traceId。這是刻意的——若這裡自行產生一個亂數,
 * 錯誤回應上的 traceId 與 OTel 送出的 trace 就是兩個不同的值,§13.6 要的關聯線索等於不存在。
 * 追蹤停用(或 tracer 不在 context 中)時才退回自行解析 W3C {@code traceparent} / 產生亂數。
 *
 * <p>同時是<strong>最外層的錯誤網</strong>:除了觀測 filter 之外它排在所有 filter 之前,
 * 任何逃出下游 filter 的例外都會經過這裡。沒有這道網,那些例外會落到 Boot 預設的 {@code /error},
 * 回出一個沒有 {@code code} 與 {@code traceId} 的結構——§9.4 的統一錯誤契約就在這條路徑上破了(ADR 0015)。
 * MVC 內部的例外由 {@code @RestControllerAdvice} 處理,不會走到這裡。
 */
@Component
@Order(TraceIdFilter.ORDER)
public class TraceIdFilter extends OncePerRequestFilter {

    /** 只讓 Boot 的觀測 filter(HIGHEST_PRECEDENCE + 1)排在前面,span 才會先於本 filter 建立。 */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 2;

    public static final String MDC_KEY = LogFields.TRACE_ID;
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);
    private static final Pattern TRACEPARENT = Pattern.compile("^[0-9a-f]{2}-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$");
    private static final Pattern REQUEST_ID = Pattern.compile("^[0-9A-Za-z._-]{1,128}$");

    private final FilterErrorWriter errorWriter;
    private final ObjectProvider<Tracer> tracer;

    public TraceIdFilter(FilterErrorWriter errorWriter, ObjectProvider<Tracer> tracer) {
        this.errorWriter = errorWriter;
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader("traceparent"));
        String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
        MDC.put(MDC_KEY, traceId);
        MDC.put(LogFields.REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } catch (Exception e) {
            // 觀測 filter 的 scope 在例外往上拋時已經關閉,MDC 的 traceId 會被拿掉——
            // 錯誤回應與這則日誌都還需要它
            MDC.put(MDC_KEY, traceId);
            if (response.isCommitted()) {
                // 回應已送出就只能讓它往上——這時再寫任何東西只會產生半截的 body
                throw e;
            }
            log.error("filter chain 逸出的未預期錯誤:{} {}", request.getMethod(), request.getRequestURI(), e);
            errorWriter.write(request, response, 500, "INTERNAL_ERROR", "Internal error");
        } finally {
            MDC.remove(MDC_KEY);
            MDC.remove(LogFields.REQUEST_ID);
        }
    }

    private String resolveTraceId(String traceparent) {
        return currentTraceId().orElseGet(() -> parseTraceParent(traceparent));
    }

    private java.util.Optional<String> currentTraceId() {
        Tracer current = tracer.getIfAvailable();
        if (current == null || current.currentSpan() == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(current.currentSpan().context().traceId());
    }

    private static String parseTraceParent(String traceparent) {
        if (traceparent != null) {
            var matcher = TRACEPARENT.matcher(traceparent);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return randomHex();
    }

    /** 用戶端可自帶 {@code X-Request-Id};格式不合(或未帶)就自己產生,不把外部字串原樣放進日誌。 */
    private static String resolveRequestId(String header) {
        return header != null && REQUEST_ID.matcher(header).matches() ? header : randomHex();
    }

    private static String randomHex() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
