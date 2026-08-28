package com.ctip.application.sync;

import com.ctip.domain.bloom.BloomVersion;
import java.util.List;
import java.util.Optional;

/**
 * {@code GET /api/v1/sync/manifest} 的內容(docs/spec/11-sync-bloom.md §11.5)。
 *
 * <p>兩個 scope 各自可能為空:public 在第一次 snapshot 產生前為空;
 * tenant 在方案沒有 tenant Bloom(§11.2 的 fail-closed 判定)或呼叫者為匿名時為空。
 * 空的表述是「你沒有這一層可同步」,不是錯誤——client 依 §11.6 只會同步它拿到的那幾層。
 *
 * <p>{@code notCovered} 與各 scope 的 {@code coverage} 為<strong>必填</strong>(§11.5);
 * 兩者的文字來自 {@code domain/bloom/BloomCoverage},與成員條件同一處維護。
 */
public record SyncManifest(
        Optional<BloomVersion> publicBloom,
        Optional<BloomVersion> tenantBloom,
        List<String> notCovered,
        int maxDeltaChain) {

    public SyncManifest {
        notCovered = List.copyOf(notCovered);
    }
}
