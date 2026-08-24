package com.ctip.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 無憑證請求一律綁定 public tenant(docs/spec/10-identity-plans.md §10.1 規則 1)。
 * M1 沒有使用者認證,所有請求皆匿名;Phase 13 在此之上加入 JWT / API key 解析,
 * 解析成功時改呼叫 {@link TenantContext#bindAuthenticated}。
 */
@Component
public class AnonymousTenantFilter extends OncePerRequestFilter {

    private final TenantContext tenantContext;

    public AnonymousTenantFilter(TenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        tenantContext.bindAnonymous();
        chain.doFilter(request, response);
    }
}
