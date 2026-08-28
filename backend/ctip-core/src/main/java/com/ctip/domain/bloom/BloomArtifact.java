package com.ctip.domain.bloom;

import java.time.Instant;
import java.util.Objects;

/**
 * 位元陣列的儲存位置與校驗資訊(docs/spec/04-data-dictionary.md 表 23),BloomVersion 的內部實體。
 *
 * <ul>
 *   <li>{@code checksum}:<strong>未壓縮</strong> payload 的 SHA-256(不變量 L5)。full 為位元陣列本身,
 *       delta 為 {@code addedBits} 的 varint payload(§11.5)
 *   <li>{@code resultingChecksum}:delta 套用後的預期位元陣列 checksum;full 為 null(不變量 L6)
 * </ul>
 */
public record BloomArtifact(
        BloomStorageKind storageKind,
        String storagePath,
        BloomCompression compression,
        long sizeBytes,
        long uncompressedSizeBytes,
        Checksum checksum,
        Checksum resultingChecksum,
        long downloadCount,
        Instant expiresAt) {

    public BloomArtifact {
        Objects.requireNonNull(storageKind, "storageKind 不得為 null");
        Objects.requireNonNull(compression, "compression 不得為 null");
        Objects.requireNonNull(checksum, "checksum 不得為 null");
        if (storagePath == null || storagePath.isBlank()) {
            throw new IllegalArgumentException("storagePath 不得為空(04 表 23 為 NOT NULL)");
        }
        if (sizeBytes < 0 || uncompressedSizeBytes < 0) {
            throw new IllegalArgumentException("artifact 大小不得為負數");
        }
        if (downloadCount < 0) {
            throw new IllegalArgumentException("downloadCount 不得為負數");
        }
    }
}
