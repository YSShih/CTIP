package com.ctip.application.sync;

import com.ctip.domain.bloom.BloomScope;
import com.ctip.domain.bloom.Checksum;
import java.util.Objects;

/**
 * {@code GET /api/v1/sync/delta?base=&scope=} 的內容(docs/spec/11-sync-bloom.md §11.5)。
 *
 * <p>{@code addedBits} 是<strong>尚未 base64url</strong> 的 varint 差分 payload:
 * §11.5 的四步編碼中,第 1–3 步是 artifact 的內容(Phase 15),第 4 步只發生在 HTTP 表述上,
 * 因此 base64url 屬 {@code interfaces/rest} 的 mapper(ADR 0024)。
 *
 * <p>{@code checksum} 是這個 payload(base64 之前)的 SHA-256;
 * {@code resultingChecksum} 是 client 套用本段後<strong>整個位元陣列</strong>應有的 SHA-256
 * ——不符即丟棄並重下 full(§11.6),這是唯一能擋住「伺服器端 artifact 已損壞」的檢查。
 */
public record SyncDelta(
        BloomScope scope,
        long datasetVersion,
        long baseVersion,
        long targetVersion,
        byte[] addedBits,
        long addedMemberCount,
        Checksum checksum,
        Checksum resultingChecksum) {

    public SyncDelta {
        Objects.requireNonNull(scope, "scope 不得為 null");
        Objects.requireNonNull(addedBits, "addedBits 不得為 null");
        Objects.requireNonNull(checksum, "checksum 不得為 null");
        Objects.requireNonNull(resultingChecksum, "resultingChecksum 不得為 null(§11.5 必填)");
        if (targetVersion < baseVersion) {
            throw new IllegalArgumentException("targetVersion 不得小於 baseVersion");
        }
    }
}
