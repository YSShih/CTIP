package com.ctip.config;

import com.ctip.application.identity.ApiKeyAuthenticator;
import com.ctip.application.identity.ApiKeySettings;
import com.ctip.application.identity.LoginPolicy;
import com.ctip.application.identity.RefreshTokenSettings;
import com.ctip.application.port.AccessTokenPort;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.PasswordHasherPort;
import com.ctip.application.port.RolePermissionRepository;
import com.ctip.application.port.SecureTokenGeneratorPort;
import com.ctip.infrastructure.audit.AuditAccessFilter;
import com.ctip.infrastructure.ratelimit.IdentityRateLimitFilter;
import com.ctip.infrastructure.security.AccessTokenIdentityResolver;
import com.ctip.infrastructure.security.BCryptPasswordHasher;
import com.ctip.infrastructure.security.CtipAuthenticationFilter;
import com.ctip.infrastructure.security.CtipPermissionEvaluator;
import com.ctip.infrastructure.security.JwtAccessTokenAdapter;
import com.ctip.infrastructure.security.SecureRandomTokenGenerator;
import com.ctip.infrastructure.security.TenantContext;
import com.ctip.infrastructure.web.FilterErrorWriter;
import com.ctip.infrastructure.web.RequestBodySizeLimitFilter;
import com.ctip.infrastructure.web.RequestBodySizeLimits;
import java.time.Duration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全層裝配(docs/spec/10-identity-plans.md §10.3–§10.5)。
 *
 * <p>授權一律以方法層 {@code @PreAuthorize} 表達(§10.3 明文),故 filter chain 對路徑
 * 全部放行;匿名亦被賦予 ANONYMOUS 角色的權限,因此權限不足是 403 而非 401
 * (缺少憑證本身不是錯誤——§10.2 匿名存取不得要求登入)。
 * 無狀態:不建 session、不用 CSRF token(§9.2 憑證走 header)。
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    private static final String IMPORT_PATH_PREFIX = "/api/v1/iocs/import";

    @Bean
    PasswordEncoder passwordEncoder() {
        // §10.4:BCrypt cost 12
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    PasswordHasherPort passwordHasherPort(PasswordEncoder encoder) {
        return new BCryptPasswordHasher(encoder);
    }

    @Bean
    SecureTokenGeneratorPort secureTokenGeneratorPort() {
        return new SecureRandomTokenGenerator();
    }

    @Bean
    AccessTokenPort accessTokenPort(CtipProperties properties, ClockPort clock) {
        CtipProperties.Jwt jwt = properties.jwt();
        return new JwtAccessTokenAdapter(jwt.secret(), Duration.ofSeconds(jwt.accessTokenExpiration()), clock);
    }

    @Bean
    RefreshTokenSettings refreshTokenSettings(CtipProperties properties) {
        CtipProperties.Jwt jwt = properties.jwt();
        return new RefreshTokenSettings(
                Duration.ofSeconds(jwt.refreshTokenExpiration()), Duration.ofDays(jwt.refreshTokenFamilyMaxDays()));
    }

    @Bean
    LoginPolicy loginPolicy(CtipProperties properties) {
        CtipProperties.Security security = properties.security();
        return new LoginPolicy(security.loginMaxFailedAttempts(), Duration.ofMinutes(security.loginLockMinutes()));
    }

    @Bean
    ApiKeySettings apiKeySettings(CtipProperties properties) {
        return new ApiKeySettings(
                switch (properties.environment()) {
                    case MVP -> "mvp";
                    case DEV -> "dev";
                    case STAGING -> "stg";
                    case PROD -> "prod";
                });
    }

    @Bean
    FilterErrorWriter filterErrorWriter(ClockPort clock) {
        return new FilterErrorWriter(clock);
    }

    /**
     * 請求本文的硬上限,排在<strong>整條 security chain 之前</strong>:
     * 這道防線要在資料進到堆積之前生效,而不是在認證之後(見 {@link RequestBodySizeLimitFilter})。
     * 目前只套用在唯一以原始 byte 陣列收檔的端點 {@code POST /api/v1/iocs/import}。
     */
    @Bean
    FilterRegistrationBean<RequestBodySizeLimitFilter> requestBodySizeLimitFilter(FilterErrorWriter errorWriter) {
        FilterRegistrationBean<RequestBodySizeLimitFilter> registration =
                new FilterRegistrationBean<>(new RequestBodySizeLimitFilter(
                        IMPORT_PATH_PREFIX, RequestBodySizeLimits.MAX_IMPORT_BYTES, errorWriter));
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 2);
        return registration;
    }

    @Bean
    CtipPermissionEvaluator ctipPermissionEvaluator() {
        return new CtipPermissionEvaluator();
    }

    @Bean
    DefaultMethodSecurityExpressionHandler methodSecurityExpressionHandler(CtipPermissionEvaluator evaluator) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(evaluator);
        return handler;
    }

    /** JWT → 身分的解析點;REST filter 與 WebSocket 握手共用(09 §9.1)。 */
    @Bean
    AccessTokenIdentityResolver accessTokenIdentityResolver(AccessTokenPort accessTokens) {
        return new AccessTokenIdentityResolver(accessTokens);
    }

    @Bean
    CtipAuthenticationFilter ctipAuthenticationFilter(
            AccessTokenIdentityResolver accessTokens,
            ApiKeyAuthenticator apiKeys,
            RolePermissionRepository rolePermissions,
            TenantContext tenantContext,
            FilterErrorWriter errorWriter) {
        return new CtipAuthenticationFilter(accessTokens, apiKeys, rolePermissions, tenantContext, errorWriter);
    }

    /**
     * 阻止 Boot 把這個 Filter bean 再自動註冊到 servlet chain 一次——
     * 它只能經 security chain 執行,否則每個請求會認證兩遍。
     */
    @Bean
    FilterRegistrationBean<CtipAuthenticationFilter> ctipAuthenticationFilterRegistration(
            CtipAuthenticationFilter filter) {
        FilterRegistrationBean<CtipAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * 限流的第二個檢查點掛在認證 filter <strong>之後</strong>:維度 1–3(apiKey / user / tenant)
     * 與維度 5 都需要已解析的身分(§10.7)。維度 4(匿名 IP)在整條 chain 之前,見
     * {@link RateLimitConfig#rateLimitFilter}——<strong>兩者必須是兩個檢查點</strong>。
     */
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CtipAuthenticationFilter authenticationFilter,
            IdentityRateLimitFilter identityRateLimitFilter,
            AuditAccessFilter auditAccessFilter)
            throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .anonymous(anonymous -> anonymous.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(identityRateLimitFilter, CtipAuthenticationFilter.class)
                // §13.5:API_ACCESS 的觸發點是「security filter chain 尾端」,而稽核要看得到
                // 已解析的身分與最終狀態碼,故排在限流之後
                .addFilterAfter(auditAccessFilter, IdentityRateLimitFilter.class)
                .build();
    }
}
