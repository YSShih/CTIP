package com.ctip.interfaces.rest.dto.sync;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * {@code GET /api/v1/sync/manifest}(docs/spec/11-sync-bloom.md §11.5)。
 *
 * <p>{@code public} 是 Java 關鍵字,因此欄位名為 {@code publicBloom} 並以
 * {@link JsonProperty} 對回 wire 上的 {@code public}——§11.5 的欄位名是對外契約。
 *
 * <p>任一層可能不存在:public 在第一次 snapshot 產生前、tenant 在方案沒有 tenant Bloom
 * 或呼叫者為匿名時。不存在的那一層<strong>整個欄位省略</strong>而不是給 {@code null}
 * ——springdoc 不會把 Java 端的 Optional 對應成 nullable,省略才讓 openapi
 * (以及由它產生的 client 型別)與 wire 上的實際形狀一致。
 * {@code notCovered} 與 {@code maxDeltaChain} 恆有值。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SyncManifestDto(
        @JsonProperty("public") BloomManifestDto publicBloom,
        BloomManifestDto tenant,
        @Schema(example = "[\"TLP:GREEN\"]") List<String> notCovered,
        @Schema(example = "24") int maxDeltaChain) {}
