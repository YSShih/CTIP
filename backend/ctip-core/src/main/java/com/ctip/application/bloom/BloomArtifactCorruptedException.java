package com.ctip.application.bloom;

/**
 * 已寫出的 artifact 與其記錄的 checksum 不符。
 *
 * <p>發生時<strong>不得</strong>繼續產生 delta:以損壞的陣列算出的 {@code resultingChecksum}
 * 會讓每一個 client 套用後自我驗證失敗(§11.6),等於整條鏈作廢卻沒有人知道原因。
 * 改為重建 full snapshot。
 */
public class BloomArtifactCorruptedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BloomArtifactCorruptedException(String message) {
        super(message);
    }
}
