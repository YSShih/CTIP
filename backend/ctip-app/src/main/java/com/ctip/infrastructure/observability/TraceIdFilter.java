package com.ctip.infrastructure.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 每請求 traceId(docs/spec/09-api.md §9.4:錯誤回應的 traceId 必須與日誌可對應)。
 * 尊重傳入的 W3C traceparent(取其 trace-id 段);否則產生 32 hex。
 * 置於 MDC 供日誌 pattern 與錯誤回應取用;M3 由 OpenTelemetry 接手。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    static final String MDC_KEY = "traceId";
    private static final Pattern TRACEPARENT = Pattern.compile("^[0-9a-f]{2}-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        MDC.put(MDC_KEY, resolveTraceId(request.getHeader("traceparent")));
        try {
            chain.doFilter(request, response);
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
