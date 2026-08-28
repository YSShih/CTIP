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
import com.ctip.infrastructure.security.BCryptPasswordHasher;
import com.ctip.infrastructure.security.CtipAuthenticationFilter;
import com.ctip.infrastructure.security.CtipPermissionEvaluator;
import com.ctip.infrastructure.security.JwtAccessTokenAdapter;
import com.ctip.infrastructure.security.SecureRandomTokenGenerator;
import com.ctip.infrastructure.security.TenantContext;
import com.ctip.infrastructure.web.FilterErrorWriter;
import java.time.Duration;
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
                },
                properties.apiKey().maxPerTenant());
    }

    @Bean
    FilterErrorWriter filterErrorWriter(ClockPort clock) {
        return new FilterErrorWriter(clock);
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

    @Bean
    CtipAuthenticationFilter ctipAuthenticationFilter(
            AccessTokenPort accessTokens,
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

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, CtipAuthenticationFilter authenticationFilter)
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
                .build();
    }
}
