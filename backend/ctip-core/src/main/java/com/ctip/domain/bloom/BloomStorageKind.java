package com.ctip.domain.bloom;

/** Artifact 的儲存後端(docs/spec/04-data-dictionary.md 表 23)。M2 只實作 FILESYSTEM。 */
public enum BloomStorageKind {
    FILESYSTEM,
    S3,
    INLINE
}
