package com.ctip.application.bloom;

import com.ctip.domain.bloom.BloomChainPolicy;
import com.ctip.domain.bloom.BloomCompression;

/**
 * Bloom 生成的設定值(對應 {@code BLOOM_*} 環境變數;由 ctip-app 的設定層建構後注入)。
 *
 * <p>{@code tenantDefaultCapacity} 是 tenant bloom 的<strong>實際尺寸預設</strong>,
 * {@code plans.tenant_bloom_capacity} 是<strong>權利上限</strong>(§11.2 授權依據)——
 * 只用方案值會讓 ENTERPRISE 的小租戶每小時產出 18MB artifact,見 ADR 0024。
 *
 * <p>tenant 沿用同一個 falsePositiveRate:§5.4 沒有 tenant 專屬的變數。
 */
public record BloomSettings(
        long publicCapacity,
        double falsePositiveRate,
        long tenantDefaultCapacity,
        BloomCompression compression,
        BloomChainPolicy chainPolicy,
        int artifactKeep) {

    public BloomSettings {
        if (publicCapacity <= 0 || tenantDefaultCapacity <= 0) {
            throw new IllegalArgumentException("bloom capacity 必須為正數");
        }
        if (artifactKeep < 1) {
            throw new IllegalArgumentException("artifactKeep 必須至少為 1:" + artifactKeep);
        }
    }
}
