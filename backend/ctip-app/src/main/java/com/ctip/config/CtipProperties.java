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
        @NotNull @Valid Proxy proxy,
        @NotNull @Valid Plan plan,
        @NotNull @Valid Ingestion ingestion,
        @NotNull @Valid Scheduler scheduler,
        @NotNull @Valid Normalization normalization,
        @NotNull @Valid Api api,
        @NotNull @Valid DataQuality dataQuality,
        @NotNull @Valid Bloom bloom,
        @NotNull @Valid Search search,
        @NotNull @Valid Notification notification,
        @NotNull @Valid Audit audit,
        @NotNull @Valid Retention retention,
        @NotNull @Valid Observability observability) {

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
     * 搜尋(docs/spec/13-platform-ops.md §13.7)。{@code backend} 決定裝配哪一套實作:
     * {@code POSTGRES} 時完全不接觸 Elasticsearch(mvp/dev 的 compose 根本不啟動它);
     * {@code ELASTICSEARCH} 時裝配 ES 讀取索引與 {@code FallbackSearchAdapter} 的降級路徑。
     *
     * <p>它與 {@code ctip.rate-limit.backend} 的差別在於:限流的後端是硬切換(Redis 連不上就啟動失敗,
     * 因為限流是安全機制),搜尋的是軟切換——§13.7 明文要求 ES 不可用時降級並回 200,
     * 因此執行期的降級由 circuit breaker 負責,這個屬性只決定「有沒有 ES 這條路」。
     *
     * @param reconcileCron 對帳排程(每日 05:00;08 §8.7 的 {@code ES_RECONCILE_CRON})
     */
    public record Search(@NotNull Backend backend, @NotBlank String reconcileCron) {

        public enum Backend {
            POSTGRES,
            ELASTICSEARCH
        }

        public boolean usesElasticsearch() {
            return backend == Backend.ELASTICSEARCH;
        }
    }

    /**
     * 信任的反向代理來源(§10.7)。空 = 不信任任何來源,{@code X-Forwarded-*} 一律忽略,
     * client IP 即直連對端——直接對外時正確,在代理後面忘了設定則所有 client 被算成同一個 IP
     * (限流過嚴而非被繞過,fail-closed)。設定方式與限制見 {@code docs/deployment/rate-limiting.md}。
     *
     * @param trusted CIDR 或單一位址,逗號分隔(對應環境變數 {@code TRUSTED_PROXIES})
     */
    public record Proxy(@NotNull java.util.List<String> trusted) {}

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
            @NotBlank String ingestionRetryCron,
            @NotBlank String tokenCleanupCron) {}

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

    /**
     * 通知與事件傳輸(docs/spec/13-platform-ops.md §13.1、§13.2)。
     *
     * <p>{@code transport} 與 {@code ctip.search.backend} 同型態的軟開關:{@code IN_PROCESS} 時
     * 完全不接觸 Kafka(mvp/dev 的 compose 根本不啟動 broker),{@code KAFKA} 時才建立
     * topic、producer 與 consumer。<strong>不是</strong>硬切換——§13.1 規則 7 明文
     * 「Kafka 不可用時不得使業務操作失敗」,執行期的不可用由轉發端的 try/catch 承擔,
     * 這個屬性只決定「有沒有 Kafka 這條路」。
     *
     * @param webhookSecretKek webhook 簽章密鑰的加密金鑰(AES-GCM;不變量 W2 定調,ADR 0021)。
     *     prod 必須來自 secret manager,啟動守衛比照 {@code JWT_SECRET}
     * @param retryCron 送達重試掃描(每 5 分鐘;08 §8.7 的 {@code NOTIFICATION_RETRY_CRON})
     * @param retryBatchSize 單次掃描最多處理幾列
     * @param deliveryTimeoutSeconds 單次 HTTP 送達的連線 + 讀取逾時
     */
    public record Notification(
            @NotNull Transport transport,
            @NotBlank String webhookSecretKek,
            @NotBlank String retryCron,
            @Positive int retryBatchSize,
            @Positive int deliveryTimeoutSeconds) {

        public enum Transport {
            IN_PROCESS,
            KAFKA
        }

        public boolean usesKafka() {
            return transport == Transport.KAFKA;
        }
    }

    /**
     * 稽核(docs/spec/13-platform-ops.md §13.5)。
     *
     * @param sampleReadRate 讀取操作的取樣率(規則 4:寫入 100%、讀取 1%;
     *     {@code AUDIT_SAMPLE_READ_RATE})。1 = 全記,整合測試用這個值
     */
    public record Audit(
            @DecimalMin("0.0") @DecimalMax("1.0") double sampleReadRate) {}

    /**
     * 資料保留政策(docs/spec/13-platform-ops.md §13.4;天數,bloomArtifactKeep 為保留份數)。
     *
     * <p>{@code username}／{@code password} 是<strong>專用的清理角色</strong>
     * ({@code ctip_retention};§13.5 規則 2)。它與應用角色是兩條不同的連線:應用角色對
     * {@code audit_logs} 連 DELETE 權限都沒有(V33 已 REVOKE),而清理角色只有各表的
     * 時間欄位可讀、只能刪該刪的列。空密碼為合法值(信任認證的部署)。
     *
     * <p>六個 cron 對應 08 §8.7 的六個保留任務。
     */
    public record Retention(
            @Positive int auditDays,
            @Positive int rawPayloadDays,
            @Positive int rejectionDays,
            @Positive int deliveryDays,
            @Positive int indicatorDays,
            @Positive int bloomArtifactKeep,
            @NotBlank String username,
            String password,
            @NotNull @Valid RetentionCrons crons) {}

    /**
     * 監控(docs/spec/13-platform-ops.md §13.6)。
     *
     * @param prometheusAllowedCidrs {@code /actuator/prometheus} 的來源 IP 白名單
     *     ({@code PROMETHEUS_ALLOWED_IPS};§13.6「prometheus 需限制來源 IP」)。
     *     空清單 = 拒絕所有來源——指標端點會洩漏租戶名稱、來源清單與流量樣態,
     *     「沒設定就全開」對安全優先的預設值是錯的方向
     * @param sourceLagRefreshMs {@code ctip.source.sync.lag} 的來源清單重整間隔
     */
    public record Observability(
            @NotNull java.util.List<String> prometheusAllowedCidrs,
            @Positive long sourceLagRefreshMs) {}

    /** 六個保留清理任務的排程(08 §8.7)。 */
    public record RetentionCrons(
            @NotBlank String audit,
            @NotBlank String rawPayload,
            @NotBlank String rejection,
            @NotBlank String bloomArtifact,
            @NotBlank String delivery,
            @NotBlank String indicator) {}
}
