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
        @NotNull @Valid Normalization normalization,
        @NotNull @Valid Stix stix,
        @NotNull @Valid DataQuality dataQuality,
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

    /**
     * 匿名限流數值依 10-identity-plans.md §10.6(60/min、1000/day);
     * M1 為 property 預設值,M2 起移入 plans 表依方案查表。
     */
    public record RateLimit(
            boolean enabled,
            @NotNull Backend backend,
            @Positive long anonymousPerMinute,
            @Positive long anonymousPerDay) {

        public enum Backend {
            MEMORY,
            REDIS
        }
    }

    public record Ingestion(boolean enabled, @Positive int batchSize) {}

    /** 排程總開關與各任務 cron(docs/spec/08-ingestion-sdk.md §8.7,皆可由環境變數覆寫)。 */
    public record Scheduler(
            boolean enabled,
            @NotBlank String sourceSyncCron,
            @NotBlank String iocExpiryCron,
            @NotBlank String ingestionRetryCron) {}

    /** `www.` 前綴去除需可設定且預設不去除(docs/spec/07-domain-intel.md §7.2)。 */
    public record Normalization(boolean stripWww) {}

    /** bundle 匯出上限(07 §7.8.5);M1 property 承載,Phase 14 移入 plans 表。 */
    public record Stix(@Positive int exportMaxObjects) {}

    /** 良性網域 allowlist(§7.3:僅 DOMAIN、exact match;預設為空)。 */
    public record DataQuality(@NotNull java.util.List<String> domainAllowlist) {}

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
