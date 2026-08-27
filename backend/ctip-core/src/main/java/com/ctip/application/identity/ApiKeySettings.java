package com.ctip.application.identity;

import java.util.Set;

/** API key 格式中的環境段(§10.5:env ∈ {mvp, dev, stg, prod});由 ctip-app 依 ENVIRONMENT 供應。 */
public record ApiKeySettings(String environment) {

    private static final Set<String> ALLOWED = Set.of("mvp", "dev", "stg", "prod");

    public ApiKeySettings {
        if (!ALLOWED.contains(environment)) {
            throw new IllegalArgumentException("API key 環境段必須是 mvp/dev/stg/prod:" + environment);
        }
    }
}
