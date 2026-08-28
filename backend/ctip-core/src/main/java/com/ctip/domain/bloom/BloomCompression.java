package com.ctip.domain.bloom;

/** Artifact 的壓縮方式(docs/spec/11-sync-bloom.md §11.4;04 表 23)。僅影響傳輸與儲存,不影響 checksum。 */
public enum BloomCompression {
    ZSTD,
    GZIP,
    NONE
}
