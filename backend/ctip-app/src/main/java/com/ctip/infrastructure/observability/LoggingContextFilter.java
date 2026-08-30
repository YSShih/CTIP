package com.ctip.infrastructure.observability;

import com.ctip.infrastructure.security.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 把已解析的身分放進 MDC:{@code tenantId} 與 {@code userId}
 * (docs/spec/13-platform-ops.md §13.6 的九個必含欄位中的兩個)。
 *
 * <p>排在認證 filter 之後——在它之前身分還不存在。API key 認證沒有 userId,
 * 這時記的是 API key 的識別碼並在值上帶 {@code apikey:} 前綴,
 * 免得「是誰做的」在日誌裡變成一個看不出型別的 UUID。
 * 由 SecurityConfig 建立(infrastructure 不得反向依賴 config,ArchUnit 規則 5)。
 */
public class LoggingContextFilter extends OncePerRequestFilter {

    private final TenantContext tenantContext;

    public LoggingContextFilter(TenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        MDC.put(LogFields.TENANT_ID, tenantContext.tenantId().value().toString());
        MDC.put(LogFields.USER_ID, actorId());
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(LogFields.TENANT_ID);
            MDC.remove(LogFields.USER_ID);
        }
    }

    private String actorId() {
        return tenantContext
                .identity()
                .map(identity -> identity.isApiKey()
                        ? "apikey:" + identity.apiKeyId().value()
                        : String.valueOf(identity.userId().value()))
                .orElse("");
    }
}
