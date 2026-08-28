package com.ctip.interfaces.rest.dto.sync;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/v1/sync/delta}(docs/spec/11-sync-bloom.md §11.5)。
 *
 * <p>{@code addedBits} 的編碼(強制):升序去重 → 差分 → LEB128 unsigned varint →
 * <strong>base64url(無 padding)</strong>。{@code checksum} 算在 base64 之前的 payload 上;
 * {@code resultingChecksum} 是套用後<strong>整個位元陣列</strong>的 SHA-256,
 * client 必須據此自我驗證,不符即丟棄並重下 full(§11.6、client 契約第 5 條)。
 */
public record SyncDeltaDto(
        @Schema(example = "PUBLIC") String scope,
        @Schema(example = "128") long datasetVersion,
        @Schema(example = "40") long baseVersion,
        @Schema(example = "42") long targetVersion,
        @Schema(example = "wYCBAsQF") String addedBits,
        @Schema(example = "15320") long addedMemberCount,

        @Schema(example = "9f2c1a7b3d5e0f4a8c6b2d1e3f5a7c9b0d2e4f6a8c1b3d5e7f9a0c2e4b6d8f1a")
        String checksum,

        @Schema(example = "3f5a1c9d0e2b4a6f8c1d3e5a7b9c1d3e5a7b9c1d3e5a7b9c1d3e5a7b9c1d3e5a")
        String resultingChecksum) {}
