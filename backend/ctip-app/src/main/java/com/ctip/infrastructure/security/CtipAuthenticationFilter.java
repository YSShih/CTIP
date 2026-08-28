package com.ctip.infrastructure.security;

import com.ctip.application.identity.ApiKeyAuthenticator;
import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.application.port.AccessTokenPort;
import com.ctip.application.port.AccessTokenVerification;
import com.ctip.application.port.RolePermissionRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.infrastructure.web.FilterErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 三種認證方式經同一條 filter(docs/spec/09-api.md §9.2):Bearer JWT、{@code X-API-Key}、無標頭匿名。
 * 一律設定 {@link TenantContext} 與 Spring SecurityContext。
 * 兩者同時提供時以 {@code Authorization} 為準並記一則 WARN。
 * auth-scheme 依 RFC 7235 大小寫不敏感;{@code Authorization} 存在但不是 Bearer 一律 401,
 * <strong>不得靜默降級為匿名</strong>——無聲吃掉憑證會讓 client 端整合問題極難查(ADR 0013)。
 * 由 SecurityConfig 建立——infrastructure 不得反向依賴 config(ArchUnit 規則 5)。
 */
public class CtipAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CtipAuthenticationFilter.class);
    private static final String BEARER = "Bearer ";

    private final AccessTokenPort accessTokens;
    private final ApiKeyAuthenticator apiKeys;
    private final RolePermissionRepository rolePermissions;
    private final TenantContext tenantContext;
    private final FilterErrorWriter errorWriter;

    public CtipAuthenticationFilter(
            AccessTokenPort accessTokens,
            ApiKeyAuthenticator apiKeys,
            RolePermissionRepository rolePermissions,
            TenantContext tenantContext,
            FilterErrorWriter errorWriter) {
        this.accessTokens = accessTokens;
        this.apiKeys = apiKeys;
        this.rolePermissions = rolePermissions;
        this.tenantContext = tenantContext;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        String apiKeyHeader = request.getHeader("X-API-Key");
        if (authorization != null) {
            if (apiKeyHeader != null) {
                log.warn("同時提供 Authorization 與 X-API-Key,以 Authorization 為準(§9.2)");
            }
            if (!isBearer(authorization)) {
                errorWriter.write(request, response, 401, "UNAUTHENTICATED", "Invalid credentials");
                return;
            }
            if (!authenticateWithJwt(authorization.substring(BEARER.length()), request, response)) {
                return;
            }
        } else if (apiKeyHeader != null) {
            if (!authenticateWithApiKey(apiKeyHeader, request, response)) {
                return;
            }
        } else {
            bindAnonymous();
        }
        chain.doFilter(request, response);
    }

    private boolean authenticateWithJwt(String token, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        AccessTokenVerification verification = accessTokens.verify(token);
        if (verification.status() == AccessTokenVerification.Status.EXPIRED) {
            errorWriter.write(request, response, 401, "TOKEN_EXPIRED", "Access token expired");
            return false;
        }
        if (verification.status() != AccessTokenVerification.Status.VALID) {
            errorWriter.write(request, response, 401, "UNAUTHENTICATED", "Invalid credentials");
            return false;
        }
        Optional<RoleCode> role = roleFrom(verification);
        if (role.isEmpty()) {
            errorWriter.write(request, response, 401, "UNAUTHENTICATED", "Invalid credentials");
            return false;
        }
        bindAuthenticated(AuthenticatedIdentity.ofUser(
                verification.claims().userId(),
                verification.claims().tenantId(),
                role.get(),
                verification.claims().permissions()));
        return true;
    }

    private boolean authenticateWithApiKey(String fullKey, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Optional<AuthenticatedIdentity> identity = apiKeys.authenticate(fullKey);
        if (identity.isEmpty()) {
            errorWriter.write(request, response, 401, "UNAUTHENTICATED", "Invalid credentials");
            return false;
        }
        bindAuthenticated(identity.get());
        return true;
    }

    private void bindAuthenticated(AuthenticatedIdentity identity) {
        tenantContext.bindAuthenticated(identity);
        SecurityContextHolder.getContext()
                .setAuthentication(new CtipAuthenticationToken(identity, CtipAuthorities.of(identity), true));
    }

    private void bindAnonymous() {
        tenantContext.bindAnonymous();
        SecurityContextHolder.getContext()
                .setAuthentication(new CtipAuthenticationToken(
                        null,
                        CtipAuthorities.of(RoleCode.ANONYMOUS, rolePermissions.permissionsOf(RoleCode.ANONYMOUS)),
                        false));
    }

    /**
     * roles claim 是單元素陣列(一使用者在一租戶內恰一個角色,表 14 的 PK 保證)。
     * 缺漏或無法辨識的角色代表 token 不是本系統簽的形狀,一律當作無效憑證——
     * 舊版退回 {@code RoleCode.USER},那是 fail-open,而且 {@code valueOf} 還會丟例外變成 500。
     */
    private static Optional<RoleCode> roleFrom(AccessTokenVerification verification) {
        return verification.claims().roles().stream().findFirst().flatMap(CtipAuthenticationFilter::parseRole);
    }

    private static Optional<RoleCode> parseRole(String code) {
        return Stream.of(RoleCode.values())
                .filter(role -> role.name().equals(code))
                .findFirst();
    }

    private static boolean isBearer(String authorization) {
        return authorization.length() > BEARER.length()
                && authorization.regionMatches(true, 0, BEARER, 0, BEARER.length());
    }
}
