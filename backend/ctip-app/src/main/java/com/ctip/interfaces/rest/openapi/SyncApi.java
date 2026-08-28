package com.ctip.interfaces.rest.openapi;

import com.ctip.domain.bloom.BloomScope;
import com.ctip.interfaces.rest.dto.common.ErrorResponse;
import com.ctip.interfaces.rest.dto.sync.SyncDeltaDto;
import com.ctip.interfaces.rest.dto.sync.SyncManifestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

/**
 * 增量同步端點的 OpenAPI 文件(§9.1「同步」、11 §11.5);controller 實作本介面以繼承註解。
 *
 * <p>描述文字刻意重述 client 契約的前兩條(§11.7):<strong>命中不代表惡意、未命中不代表安全</strong>。
 * 契約全文在 {@code docs/api/sync-client-contract.md},而 openapi.json 是 client 產生器的來源
 * ——只寫在另一份文件裡的警告,對只看 generated client 的開發者等於不存在。
 */
@Tag(
        name = "Sync",
        description = "Bloom filter manifest, snapshot download and delta sync (11 §11.5). "
                + "A Bloom hit means MAYBE PRESENT and must be verified with POST /api/v1/iocs/lookup; "
                + "a miss only means the value is not in that Bloom's member set — TLP:GREEN has no Bloom coverage.")
public interface SyncApi {

    String MANIFEST_EXAMPLE = """
            {"public":{"scope":"PUBLIC","datasetVersion":128,"bloomVersion":42,\
            "fingerprintAlgorithm":"SHA256","hashFunctionCount":10,"bitSize":143775880,\
            "capacity":10000000,"falsePositiveRate":0.001,"memberCount":8342119,\
            "checksum":"3f5a1c9d0e2b4a6f8c1d3e5a7b9c1d3e5a7b9c1d3e5a7b9c1d3e5a7b9c1d3e5a",\
            "sizeBytes":17971985,"compression":"ZSTD","generatedAt":"2026-08-21T04:00:00Z",\
            "coverage":"TLP:CLEAR only"},"notCovered":["TLP:GREEN"],"maxDeltaChain":24}""";

    String DELTA_EXAMPLE = """
            {"scope":"PUBLIC","datasetVersion":128,"baseVersion":40,"targetVersion":42,\
            "addedBits":"wYCBAsQF","addedMemberCount":15320,\
            "checksum":"9f2c1a7b3d5e0f4a8c6b2d1e3f5a7c9b0d2e4f6a8c1b3d5e7f9a0c2e4b6d8f1a",\
            "resultingChecksum":"3f5a1c9d0e2b4a6f8c1d3e5a7b9c1d3e5a7b9c1d3e5a7b9c1d3e5a7b9c1d3e5a"}""";

    String SNAPSHOT_REQUIRED_EXAMPLE = """
            {"timestamp":"2026-08-21T08:00:00Z","status":409,"code":"SNAPSHOT_REQUIRED",\
            "message":"Delta chain too long, download full snapshot","path":"/api/v1/sync/delta",\
            "traceId":"00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"}""";

    String TOO_FREQUENT_EXAMPLE = """
            {"timestamp":"2026-08-21T08:00:00Z","status":429,"code":"RATE_LIMIT_EXCEEDED",\
            "message":"Sync interval of 86400s has not elapsed yet","path":"/api/v1/sync/bloom",\
            "traceId":"00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"}""";

    @Operation(
            summary = "Get the Bloom sync manifest",
            description = "Metadata for both Bloom layers plus the mandatory coverage disclosure. "
                    + "`checksum` is the SHA-256 the local bit array must have once fully synced, and "
                    + "`sizeBytes` is the uncompressed array length; `compression` is the transport "
                    + "encoding used by GET /sync/bloom. A layer is omitted when there is nothing to "
                    + "sync there (no snapshot yet, or the plan has no tenant Bloom). This endpoint is "
                    + "not rate-limited by min_sync_interval_seconds so clients can poll it cheaply. "
                    + "認證:需要 sync:bloom(ANONYMOUS 角色亦持有)。")
    @ApiResponse(
            responseCode = "200",
            description = "Manifest for the layers the caller may sync",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SyncManifestDto.class),
                            examples = @ExampleObject(value = MANIFEST_EXAMPLE)))
    @ApiResponse(
            responseCode = "403",
            description = "Missing sync:bloom permission (FORBIDDEN)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    SyncManifestDto manifest();

    @Operation(
            summary = "Download a full Bloom snapshot",
            description = "Streams the newest full snapshot for the scope as binary, encoded with the "
                    + "compression named in the manifest. Verify X-Bloom-Checksum against the SHA-256 of "
                    + "the decompressed array before use, and store X-Bloom-Dataset-Version / X-Bloom-Version "
                    + "as your local version — the manifest's bloomVersion is the newest version reachable "
                    + "via delta, not the version of this artifact. Download authorization follows the plan "
                    + "(public_bloom_enabled / tenant_bloom_capacity) and consumes the sync interval "
                    + "(429 when called again too soon). 認證:需要 sync:bloom(ANONYMOUS 角色亦持有);"
                    + "scope=TENANT 另需方案含 tenant Bloom。")
    @ApiResponse(
            responseCode = "200",
            description = "The compressed bit array",
            headers = {
                @Header(name = "X-Bloom-Scope", description = "PUBLIC or TENANT", schema = @Schema(type = "string")),
                @Header(
                        name = "X-Bloom-Dataset-Version",
                        description = "datasetVersion of this artifact",
                        schema = @Schema(type = "integer")),
                @Header(
                        name = "X-Bloom-Version",
                        description = "bloomVersion of this artifact (0 for a full snapshot)",
                        schema = @Schema(type = "integer")),
                @Header(
                        name = "X-Bloom-Checksum",
                        description = "SHA-256 of the uncompressed bit array",
                        schema = @Schema(type = "string")),
                @Header(
                        name = "X-Bloom-Compression",
                        description = "ZSTD | GZIP | NONE",
                        schema = @Schema(type = "string")),
                @Header(
                        name = "X-Bloom-Bit-Size",
                        description = "bitSize (m); the array is ceil(m/8) bytes",
                        schema = @Schema(type = "integer")),
                @Header(
                        name = "X-Bloom-Hash-Count",
                        description = "hashFunctionCount (k)",
                        schema = @Schema(type = "integer"))
            },
            content =
                    @Content(
                            mediaType = "application/octet-stream",
                            schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(
            responseCode = "403",
            description = "PLAN_LIMIT_EXCEEDED — the plan does not include this Bloom layer",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "NOT_FOUND — no snapshot has been generated yet")
    @ApiResponse(
            responseCode = "429",
            description = "RATE_LIMIT_EXCEEDED — min_sync_interval_seconds has not elapsed (see Retry-After)",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = TOO_FREQUENT_EXAMPLE)))
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    ResponseEntity<byte[]> bloom(
            @Parameter(description = "Which Bloom layer to download", example = "PUBLIC") BloomScope scope);

    @Operation(
            summary = "Get the delta since a base version",
            description = "Returns every bit index added between `base` and the newest version of the "
                    + "current dataset, encoded as ascending-deduplicated LEB128 varint deltas in "
                    + "base64url without padding. Apply them, then verify `resultingChecksum`; on mismatch "
                    + "discard and download a full snapshot. 409 SNAPSHOT_REQUIRED when the chain is too "
                    + "long, when no snapshot exists yet, or when `base` is not part of the current dataset. "
                    + "認證:需要 sync:delta(匿名不持有)。")
    @ApiResponse(
            responseCode = "200",
            description = "Bits added between base and target",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SyncDeltaDto.class),
                            examples = @ExampleObject(value = DELTA_EXAMPLE)))
    @ApiResponse(
            responseCode = "403",
            description = "Missing sync:delta permission (FORBIDDEN) or plan without this Bloom layer",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "SNAPSHOT_REQUIRED — download a full snapshot instead",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = SNAPSHOT_REQUIRED_EXAMPLE)))
    @ApiResponse(
            responseCode = "429",
            description = "RATE_LIMIT_EXCEEDED — min_sync_interval_seconds has not elapsed (see Retry-After)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    SyncDeltaDto delta(
            @Parameter(description = "The caller's local bloomVersion", example = "40") long base,
            @Parameter(description = "Which Bloom layer to sync", example = "PUBLIC") BloomScope scope);
}
