package com.ctip.application.identity;

import java.util.Set;

/**
 * API key 的環境段與每租戶數量上限(§10.5);由 ctip-app 依 {@code ENVIRONMENT} 與設定供應。
 *
 * <p>§10.5 明文「數量上限 {@code plans.max_api_keys}」,但 {@code plans} 表要到 Phase 14 才存在。
 * 比照 ADR 0004(匿名限流數值先以 property 承載)的前例,M2 先以 {@code ctip.api-key.max-per-tenant}
 * 承載,Phase 14 改為依方案查表(ADR 0013)。
 */
public record ApiKeySettings(String environment, int maxPerTenant) {

    private static final Set<String> ALLOWED = Set.of("mvp", "dev", "stg", "prod");

    public ApiKeySettings {
        if (!ALLOWED.contains(environment)) {
            throw new IllegalArgumentException("API key 環境段必須是 mvp/dev/stg/prod:" + environment);
        }
        if (maxPerTenant < 1) {
            throw new IllegalArgumentException("每租戶 API key 上限必須 >= 1");
        }
    }
}
