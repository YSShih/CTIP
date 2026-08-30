package com.ctip.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.config.CtipProperties.Bloom;
import com.ctip.config.CtipProperties.Cors;
import com.ctip.config.CtipProperties.Environment;
import com.ctip.config.CtipProperties.Ingestion;
import com.ctip.config.CtipProperties.Jwt;
import com.ctip.config.CtipProperties.RateLimit;
import com.ctip.config.CtipProperties.Retention;
import com.ctip.config.CtipProperties.Scheduler;
import com.ctip.domain.bloom.BloomCompression;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** 啟動守衛的五條規則(docs/spec/05-environment.md §5.7;DoD M3-18 的拒絕啟動分支)。 */
@Tag("unit")
class StartupValidatorTest {

    private static final String REAL_SECRET = "a-real-secret-with-enough-length-0123456789";

    @Test
    void prodRejectsTemplateJwtSecret() {
        StartupValidator validator = validator(
                Environment.PROD,
                "CHANGE_ME_MIN_32_BYTES_REPLACE_THIS",
                "https://ctip.example.com",
                RateLimit.Backend.REDIS,
                validateEnv());
        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void prodRejectsShortJwtSecret() {
        StartupValidator validator = validator(
                Environment.PROD, "too-short", "https://ctip.example.com", RateLimit.Backend.REDIS, validateEnv());
        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    void prodRejectsWildcardCors() {
        StartupValidator validator =
                validator(Environment.PROD, REAL_SECRET, "*", RateLimit.Backend.REDIS, validateEnv());
        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS");
    }

    @Test
    void nonMvpRejectsNonValidateDdlAuto() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.jpa.hibernate.ddl-auto", "update");
        StartupValidator validator =
                validator(Environment.DEV, REAL_SECRET, "http://localhost:5173", RateLimit.Backend.REDIS, env);
        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ddl-auto");
    }

    @Test
    void prodWithValidConfigurationStarts() {
        StartupValidator validator = validator(
                Environment.PROD, REAL_SECRET, "https://ctip.example.com", RateLimit.Backend.REDIS, validateEnv());
        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void mvpAllowsTemplateSecretAndMemoryRateLimit() {
        StartupValidator validator = validator(
                Environment.MVP,
                "CHANGE_ME_MIN_32_BYTES_REPLACE_THIS",
                "http://localhost:5173",
                RateLimit.Backend.MEMORY,
                new MockEnvironment());
        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void stagingWarnsButStartsWithMemoryRateLimit() {
        StartupValidator validator = validator(
                Environment.STAGING,
                REAL_SECRET,
                "https://ctip-staging.example.com",
                RateLimit.Backend.MEMORY,
                validateEnv());
        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    private static MockEnvironment validateEnv() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.jpa.hibernate.ddl-auto", "validate");
        return env;
    }

    private static StartupValidator validator(
            Environment environment,
            String jwtSecret,
            String corsOrigins,
            RateLimit.Backend backend,
            MockEnvironment springEnv) {
        CtipProperties properties = new CtipProperties(
                environment,
                new Cors(corsOrigins),
                new Jwt(jwtSecret, 900, 2592000, 90),
                new CtipProperties.Security(10, 15),
                new RateLimit(true, backend),
                new CtipProperties.Proxy(java.util.List.of()),
                new CtipProperties.Plan(""),
                new Ingestion(true, 500),
                new Scheduler(true, "0 */5 * * * *", "0 0 3 * * *", "0 */15 * * * *", "0 0 2 * * *"),
                new CtipProperties.Normalization(false),
                new CtipProperties.Api(50),
                new CtipProperties.DataQuality(java.util.List.of()),
                new Bloom(
                        10_000_000,
                        0.001,
                        1_000_000,
                        "0 0 4 * * *",
                        "0 0 * * * *",
                        24,
                        "/var/lib/ctip/bloom",
                        BloomCompression.ZSTD),
                new CtipProperties.Search(CtipProperties.Search.Backend.POSTGRES, "0 0 5 * * *"),
                new CtipProperties.Notification(
                        CtipProperties.Notification.Transport.IN_PROCESS,
                        "unit-test-webhook-kek-0123456789abcdef",
                        "0 */5 * * * *",
                        200,
                        10),
                new CtipProperties.Audit(1.0),
                new Retention(
                        180,
                        30,
                        30,
                        30,
                        365,
                        30,
                        "ctip_retention",
                        "unit-test",
                        new CtipProperties.RetentionCrons(
                                "0 0 1 * * SUN",
                                "0 30 1 * * *",
                                "0 40 1 * * *",
                                "0 50 1 * * *",
                                "0 10 2 * * *",
                                "0 20 2 * * *")),
                new CtipProperties.Observability(java.util.List.of("127.0.0.1/32"), 60_000L));
        return new StartupValidator(properties, springEnv);
    }
}
