package com.ctip.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 所有 {@code ctip.*} 屬性的唯一綁定點(docs/spec/05-environment.md §5.7)。
 * 環境變數 → Spring property 的對應在 application.yml;此處禁止散落的 {@code @Value}。
 */
@ConfigurationProperties(prefix = "ctip")
@Validated
public record CtipProperties(
        @NotNull Environment environment,
        @NotNull @Valid Cors cors,
        @NotNull @Valid Jwt jwt,
        @NotNull @Valid RateLimit rateLimit,
        @NotNull @Valid Ingestion ingestion,
        @NotNull @Valid Scheduler scheduler,
        @NotNull @Valid Bloom bloom,
        @NotNull @Valid Retention retention) {

    /** 執行環境,對應環境變數 ENVIRONMENT(mvp | dev | staging | prod)。 */
    public enum Environment {
        MVP,
        DEV,
        STAGING,
        PROD
    }

    public record Cors(@NotBlank String allowedOrigins) {}

    public record Jwt(
            @NotBlank String secret,
            @Positive long accessTokenExpiration,
            @Positive long refreshTokenExpiration) {}

    public record RateLimit(boolean enabled, @NotNull Backend backend) {

        public enum Backend {
            MEMORY,
            REDIS
        }
    }

    public record Ingestion(boolean enabled, @Positive int batchSize) {}

    public record Scheduler(boolean enabled) {}

    public record Bloom(
            @Positive long publicCapacity,

            @DecimalMin(value = "0", inclusive = false) @DecimalMax(value = "1", inclusive = false)
            double publicFalsePositiveRate,

            @Positive long tenantDefaultCapacity,
            @NotBlank String snapshotCron,
            @NotBlank String deltaCron,
            @Min(1) @Max(1000) int maxDeltaChain) {}

    /** 資料保留政策(天數;bloomArtifactKeep 為保留份數)。 */
    public record Retention(
            @Positive int auditDays,
            @Positive int rawPayloadDays,
            @Positive int rejectionDays,
            @Positive int deliveryDays,
            @Positive int indicatorDays,
            @Positive int bloomArtifactKeep) {}
}
