package com.ctip.domain.source;

/** 來源信譽 0–100,合併加權用;&gt;= 80 為可信任撤回門檻(docs/spec/07-domain-intel.md §7.5)。 */
public record Reputation(int value) {

    private static final int TRUSTED_RETRACTION_THRESHOLD = 80;

    public Reputation {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException("reputation 必須在 0–100 之間:" + value);
        }
    }

    public boolean isTrustedForRetraction() {
        return value >= TRUSTED_RETRACTION_THRESHOLD;
    }
}
