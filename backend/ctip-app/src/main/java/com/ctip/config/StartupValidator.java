package com.ctip.config;

import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 啟動守衛(docs/spec/05-environment.md §5.7,強制)。
 * 「拒絕啟動」的條件丟出 {@link IllegalStateException} 中止啟動;其餘條件記 WARN。
 */
@Component
public class StartupValidator implements InitializingBean {

    /** JWT_SECRET 樣板值的標記字串,與 environment/.env.*.example 及 up.sh 的檢查一致。 */
    static final String JWT_SECRET_TEMPLATE_MARKER = "CHANGE_ME";

    private static final int JWT_SECRET_MIN_BYTES = 32;
    private static final Logger log = LoggerFactory.getLogger(StartupValidator.class);

    private final CtipProperties properties;
    private final Environment springEnvironment;

    public StartupValidator(CtipProperties properties, Environment springEnvironment) {
        this.properties = properties;
        this.springEnvironment = springEnvironment;
    }

    @Override
    public void afterPropertiesSet() {
        validate();
    }

    void validate() {
        if (properties.environment() == CtipProperties.Environment.PROD) {
            requireRealJwtSecret();
            requireRestrictedCors();
            warnIfSwaggerEnabled();
        }
        if (properties.environment() != CtipProperties.Environment.MVP) {
            warnIfMemoryRateLimit();
            warnIfNoTrustedProxies();
            requireDdlAutoValidate();
        }
    }

    private void requireRealJwtSecret() {
        String secret = properties.jwt().secret();
        if (secret.contains(JWT_SECRET_TEMPLATE_MARKER)) {
            throw new IllegalStateException("ENVIRONMENT=prod 但 JWT_SECRET 仍是樣板值;請改用 secret manager 提供的真實值");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < JWT_SECRET_MIN_BYTES) {
            throw new IllegalStateException("ENVIRONMENT=prod 但 JWT_SECRET 長度不足 " + JWT_SECRET_MIN_BYTES + " bytes");
        }
    }

    private void requireRestrictedCors() {
        if (properties.cors().allowedOrigins().contains("*")) {
            throw new IllegalStateException("ENVIRONMENT=prod 但 CORS_ALLOWED_ORIGINS 含 *;prod 必須列舉明確的 origin");
        }
    }

    private void warnIfSwaggerEnabled() {
        if (springEnvironment.getProperty("springdoc.api-docs.enabled", Boolean.class, false)) {
            log.warn("ENVIRONMENT=prod 但 SWAGGER_ENABLED=true;允許但必須另有存取保護(docs/spec/05-environment.md §5.7)");
        }
    }

    private void warnIfMemoryRateLimit() {
        if (properties.rateLimit().backend() == CtipProperties.RateLimit.Backend.MEMORY) {
            log.warn("ENVIRONMENT={} 但 RATE_LIMIT_BACKEND=memory;memory 後端僅適用於 mvp", properties.environment());
        }
    }

    /**
     * §10.7:「若無法確定真實 client IP,{@code docs/deployment/} 必須明確記載此限制」。
     * mvp 以外的環境一律在反向代理(nginx)後面,{@code TRUSTED_PROXIES} 空著代表
     * 所有請求都被算成代理的位址——限流會過嚴而不是被繞過(fail-closed),
     * 但那不是預期的運作方式,不得靜默略過。
     */
    private void warnIfNoTrustedProxies() {
        if (properties.proxy().trusted().isEmpty()) {
            log.warn(
                    "ENVIRONMENT={} 但 TRUSTED_PROXIES 為空;X-Forwarded-* 一律不採信,"
                            + "反向代理後方的所有 client 會被視為同一個 IP(見 docs/deployment/rate-limiting.md)",
                    properties.environment());
        }
    }

    private void requireDdlAutoValidate() {
        String ddlAuto = springEnvironment.getProperty("spring.jpa.hibernate.ddl-auto");
        if (!"validate".equals(ddlAuto)) {
            throw new IllegalStateException("ENVIRONMENT=" + properties.environment()
                    + " 但 spring.jpa.hibernate.ddl-auto=" + ddlAuto + ";schema 一律由 Flyway 管理,必須為 validate");
        }
    }
}
