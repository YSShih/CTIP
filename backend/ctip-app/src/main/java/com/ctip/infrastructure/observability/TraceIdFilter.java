package com.ctip.infrastructure.observability;

import com.ctip.infrastructure.web.FilterErrorWriter;
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
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 每請求 traceId(docs/spec/09-api.md §9.4:錯誤回應的 traceId 必須與日誌可對應)。
 * 尊重傳入的 W3C traceparent(取其 trace-id 段);否則產生 32 hex。
 * 置於 MDC 供日誌 pattern 與錯誤回應取用;M3 由 OpenTelemetry 接手。
 *
 * <p>同時是<strong>最外層的錯誤網</strong>:它排在 HIGHEST_PRECEDENCE,任何逃出下游 filter 的
 * 例外都會經過這裡。沒有這道網,那些例外會落到 Boot 預設的 {@code /error},回出一個沒有
 * {@code code} 與 {@code traceId} 的結構——§9.4 的統一錯誤契約就在這條路徑上破了(ADR 0015)。
 * MVC 內部的例外由 {@code @RestControllerAdvice} 處理,不會走到這裡。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    static final String MDC_KEY = "traceId";
    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);
    private static final Pattern TRACEPARENT = Pattern.compile("^[0-9a-f]{2}-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$");

    private final FilterErrorWriter errorWriter;

    public TraceIdFilter(FilterErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        MDC.put(MDC_KEY, resolveTraceId(request.getHeader("traceparent")));
        try {
            chain.doFilter(request, response);
        } catch (Exception e) {
            // 回應已送出就只能讓它往上——這時再寫任何東西只會產生半截的 body
            if (response.isCommitted()) {
                throw e;
            }
            log.error("filter chain 逸出的未預期錯誤:{} {}", request.getMethod(), request.getRequestURI(), e);
            errorWriter.write(request, response, 500, "INTERNAL_ERROR", "Internal error");
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String resolveTraceId(String traceparent) {
        if (traceparent != null) {
            var matcher = TRACEPARENT.matcher(traceparent);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
