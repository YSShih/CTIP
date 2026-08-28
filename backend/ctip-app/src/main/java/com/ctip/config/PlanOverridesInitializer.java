package com.ctip.config;

import com.ctip.application.port.PlanRepository;
import com.ctip.domain.plan.Plan;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.plan.QuotaLimit;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 部署期的方案配額覆寫(docs/spec/10-identity-plans.md §10.6;ADR 0019)。
 *
 * <p>單一 JSON 變數 {@code CTIP_PLAN_OVERRIDES},例如 {@code {"PREMIUM":{"maxApiKeys":20}}}。
 * <strong>不是</strong>逐項環境變數:{@code CTIP_PLAN_PREMIUM_MAX_API_KEYS} 會被 Spring 的
 * relaxed binding 對到 {@code ctip.plan.premium.max.api.keys}(底線一律變成點,不會變成連字號),
 * 永遠綁不到目標屬性——設定看似可調、實際完全無效。
 *
 * <p>未知的方案代碼或欄位名一律<strong>啟動即失敗</strong>:打錯字而被靜默忽略,
 * 等於營運方以為配額調過了、實際上沒有,那比起不了機器更危險。
 *
 * <p>以 {@link ApplicationRunner} 執行:必須在 Flyway 之後(plans 表要先存在)。
 */
@Component
class PlanOverridesInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlanOverridesInitializer.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final PlanRepository plans;
    private final String overrides;

    PlanOverridesInitializer(PlanRepository plans, CtipProperties properties) {
        this.plans = plans;
        this.overrides = properties.plan().overrides();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (overrides == null || overrides.isBlank()) {
            return;
        }
        JsonNode root = readTree();
        for (Map.Entry<String, JsonNode> entry : root.properties()) {
            PlanCode code = planCode(entry.getKey());
            Plan current = plans.findByCode(code)
                    .orElseThrow(() -> new IllegalStateException("CTIP_PLAN_OVERRIDES 指向不存在的方案:" + code));
            Plan updated = apply(current, entry.getValue());
            if (!updated.equals(current)) {
                plans.save(updated);
                log.info("套用 CTIP_PLAN_OVERRIDES:{} 的配額已更新", code);
            }
        }
    }

    private static PlanCode planCode(String raw) {
        try {
            return PlanCode.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("CTIP_PLAN_OVERRIDES 含未知的方案代碼:" + raw, e);
        }
    }

    /** 逐欄覆寫;JSON 的 {@code null} 代表「無限制」,與 plans 表的 NULL 語意一致。 */
    private static Plan apply(Plan plan, JsonNode fields) {
        Draft draft = new Draft(plan);
        for (Map.Entry<String, JsonNode> field : fields.properties()) {
            draft.set(field.getKey(), field.getValue());
        }
        return draft.toPlan();
    }

    /**
     * 覆寫用的可變草稿。
     *
     * <p>{@link Plan} 有 18 個成員,若對每個可覆寫欄位各寫一次 18 引數的建構呼叫,
     * 那是十四段只差一個引數的複製貼上——改一個欄位順序就會有十四處要同步。
     */
    private static final class Draft {

        private final Plan original;
        private QuotaLimit requestsPerMinute;
        private QuotaLimit requestsPerDay;
        private int maxPageSize;
        private QuotaLimit maxBatchLookup;
        private int minSyncIntervalSeconds;
        private boolean publicBloomEnabled;
        private QuotaLimit tenantBloomCapacity;
        private boolean websocketEnabled;
        private QuotaLimit maxWebhooks;
        private QuotaLimit maxApiKeys;
        private boolean customFeedEnabled;
        private QuotaLimit stixExportMaxObjects;
        private QuotaLimit maxManualSubmissionsPerDay;
        private QuotaLimit maxImportRowsPerFile;

        private Draft(Plan plan) {
            this.original = plan;
            this.requestsPerMinute = plan.requestsPerMinute();
            this.requestsPerDay = plan.requestsPerDay();
            this.maxPageSize = plan.maxPageSize();
            this.maxBatchLookup = plan.maxBatchLookup();
            this.minSyncIntervalSeconds = plan.minSyncIntervalSeconds();
            this.publicBloomEnabled = plan.publicBloomEnabled();
            this.tenantBloomCapacity = plan.tenantBloomCapacity();
            this.websocketEnabled = plan.websocketEnabled();
            this.maxWebhooks = plan.maxWebhooks();
            this.maxApiKeys = plan.maxApiKeys();
            this.customFeedEnabled = plan.customFeedEnabled();
            this.stixExportMaxObjects = plan.stixExportMaxObjects();
            this.maxManualSubmissionsPerDay = plan.maxManualSubmissionsPerDay();
            this.maxImportRowsPerFile = plan.maxImportRowsPerFile();
        }

        private void set(String field, JsonNode value) {
            switch (field) {
                case "requestsPerMinute" -> requestsPerMinute = quota(value);
                case "requestsPerDay" -> requestsPerDay = quota(value);
                case "maxPageSize" -> maxPageSize = value.asInt();
                case "maxBatchLookup" -> maxBatchLookup = quota(value);
                case "minSyncIntervalSeconds" -> minSyncIntervalSeconds = value.asInt();
                case "publicBloomEnabled" -> publicBloomEnabled = value.asBoolean();
                case "tenantBloomCapacity" -> tenantBloomCapacity = quota(value);
                case "websocketEnabled" -> websocketEnabled = value.asBoolean();
                case "maxWebhooks" -> maxWebhooks = quota(value);
                case "maxApiKeys" -> maxApiKeys = quota(value);
                case "customFeedEnabled" -> customFeedEnabled = value.asBoolean();
                case "stixExportMaxObjects" -> stixExportMaxObjects = quota(value);
                case "maxManualSubmissionsPerDay" -> maxManualSubmissionsPerDay = quota(value);
                case "maxImportRowsPerFile" -> maxImportRowsPerFile = quota(value);
                default -> throw new IllegalStateException("CTIP_PLAN_OVERRIDES 含未知的欄位名:" + field);
            }
        }

        private Plan toPlan() {
            return new Plan(
                    original.id(),
                    original.code(),
                    original.name(),
                    original.tier(),
                    requestsPerMinute,
                    requestsPerDay,
                    maxPageSize,
                    maxBatchLookup,
                    minSyncIntervalSeconds,
                    publicBloomEnabled,
                    tenantBloomCapacity,
                    websocketEnabled,
                    maxWebhooks,
                    maxApiKeys,
                    customFeedEnabled,
                    stixExportMaxObjects,
                    maxManualSubmissionsPerDay,
                    maxImportRowsPerFile);
        }
    }

    private static QuotaLimit quota(JsonNode value) {
        return value.isNull() ? QuotaLimit.unlimited() : QuotaLimit.of(value.asLong());
    }

    private JsonNode readTree() {
        try {
            return MAPPER.readTree(overrides);
        } catch (JacksonException e) {
            throw new IllegalStateException("CTIP_PLAN_OVERRIDES 不是合法的 JSON", e);
        }
    }
}
