package com.ctip.interfaces.rest.mapper;

import com.ctip.application.sync.SyncDelta;
import com.ctip.application.sync.SyncManifest;
import com.ctip.domain.bloom.BloomCoverage;
import com.ctip.domain.bloom.BloomVersion;
import com.ctip.interfaces.rest.dto.sync.BloomManifestDto;
import com.ctip.interfaces.rest.dto.sync.SyncDeltaDto;
import com.ctip.interfaces.rest.dto.sync.SyncManifestDto;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * 同步 metadata → DTO(手寫;§9.5 禁止把聚合直接暴露於 API)。
 *
 * <p>{@code addedBits} 的<strong>第 4 步 base64url(無 padding)</strong>在這裡發生:
 * §11.5 的四步編碼中,前三步的產物就是 artifact 的內容(Phase 15 已落檔並據此算 checksum),
 * 只有 base64url 純屬 HTTP 表述——放在 domain 會讓「artifact 內容」多出一種表示法(ADR 0024)。
 */
@Component
public class SyncDtoMapper {

    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

    public SyncManifestDto toDto(SyncManifest manifest) {
        return new SyncManifestDto(
                manifest.publicBloom().map(SyncDtoMapper::toScopeDto).orElse(null),
                manifest.tenantBloom().map(SyncDtoMapper::toScopeDto).orElse(null),
                manifest.notCovered(),
                manifest.maxDeltaChain());
    }

    public SyncDeltaDto toDto(SyncDelta delta) {
        return new SyncDeltaDto(
                delta.scope().name(),
                delta.datasetVersion(),
                delta.baseVersion(),
                delta.targetVersion(),
                BASE64URL.encodeToString(delta.addedBits()),
                delta.addedMemberCount(),
                delta.checksum().hex(),
                delta.resultingChecksum().hex());
    }

    /**
     * 一層 Bloom 的「你完全同步後應該是什麼狀態」。
     *
     * <p>{@code checksum} 取 {@link BloomVersion#arrayChecksum()}——即套用到這個版本為止的
     * <strong>位元陣列</strong> checksum,不是 artifact payload 的 checksum:最新版本若是 delta,
     * 後者算的是那段 varint payload,client 拿它驗自己的陣列永遠不會相符。
     *
     * <p>{@code sizeBytes} 取<strong>未壓縮</strong>陣列長度 {@code ceil(bitSize / 8)}:
     * §11.5 範例的 17,971,985 正是 {@code 143775880 / 8},而該範例的 {@code compression}
     * 是 {@code ZSTD}——若那個欄位指壓縮後大小,兩個數字不可能相等。壓縮後的實際位元組數
     * 由 {@code /sync/bloom} 回應的 {@code Content-Length} 表達。
     */
    private static BloomManifestDto toScopeDto(BloomVersion version) {
        return new BloomManifestDto(
                version.scope().name(),
                version.datasetVersion(),
                version.bloomVersion(),
                version.parameters().algorithm().name(),
                version.parameters().hashFunctionCount(),
                version.parameters().bitSize(),
                version.parameters().capacity(),
                version.parameters().falsePositiveRate(),
                version.memberCount(),
                version.arrayChecksum().hex(),
                version.parameters().byteLength(),
                version.artifact().compression().name(),
                version.generatedAt(),
                BloomCoverage.describe(version.scope()));
    }
}
