package com.ctip.application.identity;

import java.util.Set;

/**
 * API key 的環境段(§10.5 金鑰格式 {@code ctip_<env>_<random>});由 ctip-app 依 {@code ENVIRONMENT} 供應。
 *
 * <p>每租戶數量上限<strong>不在此</strong>:§10.5 明文「數量上限 {@code plans.max_api_keys}」,
 * Phase 14 起由 {@code QuotaService} 依方案查表。留一份 property 就是第二個真相來源。
 */
public record ApiKeySettings(String environment) {

    private static final Set<String> ALLOWED = Set.of("mvp", "dev", "stg", "prod");

    public ApiKeySettings {
        if (!ALLOWED.contains(environment)) {
            throw new IllegalArgumentException("API key 環境段必須是 mvp/dev/stg/prod:" + environment);
        }
    }
}
