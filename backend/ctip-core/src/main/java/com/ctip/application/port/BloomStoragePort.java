package com.ctip.application.port;

import com.ctip.domain.bloom.BloomArtifactLocation;
import com.ctip.domain.bloom.BloomCompression;

/**
 * Bloom artifact 的儲存 port(docs/spec/04-data-dictionary.md 表 23;
 * M2 只有 {@code FILESYSTEM} 實作,路徑根由 {@code BLOOM_STORAGE_DIR} 指定)。
 *
 * <p>寫入的一律是<strong>未壓縮</strong>的 payload;壓縮由實作負責,
 * 因為 checksum(不變量 L5)必須算在未壓縮內容上——壓縮只影響傳輸與儲存。
 */
public interface BloomStoragePort {

    StoredArtifact write(BloomArtifactLocation location, byte[] uncompressed, BloomCompression compression);

    /** 讀回未壓縮 payload;檔案不存在或解壓失敗一律丟例外,不得回傳空陣列(空陣列是合法的 delta)。 */
    byte[] read(String storagePath, BloomCompression compression);

    /**
     * 讀回<strong>儲存時的原始位元組</strong>(即已依 {@code compression} 壓縮過的內容)。
     *
     * <p>{@code GET /api/v1/sync/bloom} 直接把它串流出去(§11.5):§11.4 明訂壓縮「僅影響傳輸」,
     * 而 manifest 的 {@code compression} 就是在告訴 client 要用哪個解碼器。
     * 在伺服器端解壓再重壓一次,除了浪費 18MB 的往返之外不會產生任何差別。
     */
    byte[] readStored(String storagePath);

    void delete(String storagePath);

    record StoredArtifact(String storagePath, long sizeBytes, long uncompressedSizeBytes) {}
}
