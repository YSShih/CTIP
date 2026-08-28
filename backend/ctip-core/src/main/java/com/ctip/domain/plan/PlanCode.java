package com.ctip.domain.plan;

/**
 * 方案代碼(docs/spec/10-identity-plans.md §10.6;對應 plans.code 與 plans.tier)。
 * tier 只用於排序與比較,配額值一律讀 plans 表,不得由 tier 推導。
 */
public enum PlanCode {
    ANONYMOUS(0),
    FREE(1),
    PREMIUM(2),
    ENTERPRISE(3);

    private final int tier;

    PlanCode(int tier) {
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }
}
