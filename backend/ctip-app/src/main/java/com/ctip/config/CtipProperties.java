package com.ctip.config;

import com.ctip.domain.bloom.BloomCompression;
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
        @NotNull @Valid Security security,
        @NotNull @Valid RateLimit rateLimit,
        @NotNull @Valid Plan plan,
        @NotNull @Valid Ingestion ingestion,
        @NotNull @Valid Scheduler scheduler,
        @NotNull @Valid Normalization normalization,
        @NotNull @Valid Api api,
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

    /**
     * {@code refreshTokenFamilyMaxDays} 是輪替家族的絕對存活上限(§10.4 未定義,ADR 0013 取 90 天):
     * 每次輪替都給滿 ttl,沒有上限則竊得一枚 token 者可無限期續期。
     */
    public record Jwt(
            @NotBlank String secret,
            @Positive long accessTokenExpiration,
            @Positive long refreshTokenExpiration,
            @Positive long refreshTokenFamilyMaxDays) {}

    /** 登入鎖定門檻(docs/spec/10-identity-plans.md §10.4:連續失敗 10 次 → 鎖定 15 分鐘)。 */
    public record Security(
            @Positive int loginMaxFailedAttempts, @Positive int loginLockMinutes) {}

    /**
     * 匿名限流數值原為 property 預設值(60/min、1000/day);Phase 14 起改由 plans 表的
     * ANONYMOUS 方案查表(§10.7 明文「Phase 14 移入 plans 表」),故此處只剩開關與後端選擇。
     */
    public record RateLimit(boolean enabled, @NotNull Backend backend) {

        public enum Backend {
            MEMORY,
            REDIS
        }
    }

    /**
     * 方案配額的部署期覆寫(§10.6;ADR 0019)。
     *
     * <p>單一 JSON 字串而非逐項環境變數:{@code CTIP_PLAN_PREMIUM_MAX_API_KEYS} 會被
     * relaxed binding 對到 {@code ctip.plan.premium.max.api.keys}(底線一律變成點,
     * 不會變成連字號),永遠綁不到目標屬性。空字串 = 不覆寫。
     *
     * @param overrides 形如 {@code {"PREMIUM":{"maxApiKeys":20}}}
     */
    public record Plan(String overrides) {}

    public record Ingestion(boolean enabled, @Positive int batchSize) {}

    /** 排程總開關與各任務 cron(docs/spec/08-ingestion-sdk.md §8.7,皆可由環境變數覆寫)。 */
    public record Scheduler(
            boolean enabled,
            @NotBlank String sourceSyncCron,
            @NotBlank String iocExpiryCron,
            @NotBlank String ingestionRetryCron) {}

    /** `www.` 前綴去除需可設定且預設不去除(docs/spec/07-domain-intel.md §7.2)。 */
    public record Normalization(boolean stripWww) {}

    /**
     * 預設分頁大小(09 §9.3)。分頁上限、批次驗證上限、bundle 物件數、API key 數量
     * 一律讀 plans 表(§10.6「不得 hard-code」),Phase 14 起不再有 property 版本。
     */
    public record Api(@Positive int defaultPageSize) {}

    /** 良性網域 allowlist(§7.3:僅 DOMAIN、exact match;預設為空)。 */
    public record DataQuality(@NotNull java.util.List<String> domainAllowlist) {}

    public record Bloom(
            @Positive long publicCapacity,

            @DecimalMin(value = "0", inclusive = false) @DecimalMax(value = "1", inclusive = false)
            double publicFalsePositiveRate,

            @Positive long tenantDefaultCapacity,
            @NotBlank String snapshotCron,
            @NotBlank String deltaCron,
            @Min(1) @Max(1000) int maxDeltaChain,
            @NotBlank String storageDir,
            @NotNull BloomCompression compression) {}

    /** 資料保留政策(天數;bloomArtifactKeep 為保留份數)。 */
    public record Retention(
            @Positive int auditDays,
            @Positive int rawPayloadDays,
            @Positive int rejectionDays,
            @Positive int deliveryDays,
            @Positive int indicatorDays,
            @Positive int bloomArtifactKeep) {}
}
