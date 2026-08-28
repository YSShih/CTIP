package com.ctip.interfaces.rest.dto.sync;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 單一層 Bloom 的 metadata(docs/spec/11-sync-bloom.md §11.5 的 {@code public} / {@code tenant} 區塊)。
 *
 * <p>{@code coverage} 為<strong>必填</strong>:client 開發者必須在 manifest 就看到覆蓋範圍限制,
 * 否則「Bloom miss = 安全」這個錯誤結論幾乎必然發生(§11.1、不變量 L8)。
 *
 * <p>{@code sizeBytes} 是<strong>未壓縮</strong>位元陣列的長度({@code ceil(bitSize / 8)}),
 * 與 §11.5 範例的 17,971,985 一致;{@code compression} 描述的是 {@code /sync/bloom}
 * 傳輸時的編碼(§11.4「僅影響傳輸」),client 先解壓再驗 {@code checksum}。
 */
public record BloomManifestDto(
        @Schema(example = "PUBLIC") String scope,
        @Schema(example = "128") long datasetVersion,
        @Schema(example = "42") long bloomVersion,
        @Schema(example = "SHA256") String fingerprintAlgorithm,
        @Schema(example = "10") int hashFunctionCount,
        @Schema(example = "143775880") long bitSize,
        @Schema(example = "10000000") long capacity,
        @Schema(example = "0.001") double falsePositiveRate,
        @Schema(example = "8342119") long memberCount,

        @Schema(example = "3f5a1c9d0e2b4a6f8c1d3e5a7b9c1d3e5a7b9c1d3e5a7b9c1d3e5a7b9c1d3e5a")
        String checksum,

        @Schema(example = "17971985") long sizeBytes,
        @Schema(example = "ZSTD") String compression,
        @Schema(example = "2026-08-21T04:00:00Z") Instant generatedAt,
        @Schema(example = "TLP:CLEAR only") String coverage) {}
