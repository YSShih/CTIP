package com.ctip.application.sync;

import com.ctip.domain.bloom.BloomVersion;
import java.util.Objects;

/**
 * {@code GET /api/v1/sync/bloom?scope=} 的回應內容(docs/spec/11-sync-bloom.md §11.5)。
 *
 * <p>{@code content} 是 artifact 在儲存體中的<strong>原始位元組</strong>,亦即已依
 * {@code version.artifact().compression()} 壓縮過的內容;client 先解壓、再驗 {@code checksum}
 * (checksum 一律算在未壓縮的位元陣列上,不變量 L5)。
 *
 * <p>§11.5 允許「302 至簽章下載 URL」或「直接回二進位串流」兩種。此處採後者:
 * 前者需要一組簽章金鑰設定,而 §5.4 沒有任何對應的環境變數——為了它新增設定項,
 * 等於為「目前只有 FILESYSTEM 一種 storage_kind」的情境預先建置(規則 18)。
 *
 * <p>刻意<strong>不</strong>做位元組陣列的防禦性複製:它是一次性的傳遞載具,內容剛從儲存體讀出、
 * 讀完即寫入回應。public bloom 一份 18MB,複製一次就是每個下載多配置 18MB。
 */
public record BloomDownload(BloomVersion version, byte[] content) {

    public BloomDownload {
        Objects.requireNonNull(version, "version 不得為 null");
        Objects.requireNonNull(content, "content 不得為 null");
    }
}
