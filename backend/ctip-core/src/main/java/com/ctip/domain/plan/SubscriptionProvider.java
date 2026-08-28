package com.ctip.domain.plan;

/**
 * 金流供應商(docs/spec/10-identity-plans.md §10.6「金流」)。
 * MVP 與 M2 皆不串接金流:只會出現 {@code NONE} 與 {@code MANUAL};
 * {@code STRIPE} 是 {@code SubscriptionProvider} 抽象的擴充點,由未來的供應商實作填入。
 */
public enum SubscriptionProvider {
    NONE,
    STRIPE,
    MANUAL
}
