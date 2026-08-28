package com.ctip.interfaces.rest;

import com.ctip.application.sync.BloomDownload;
import com.ctip.application.sync.SyncService;
import com.ctip.domain.bloom.BloomScope;
import com.ctip.domain.bloom.BloomVersion;
import com.ctip.infrastructure.security.ClientSubject;
import com.ctip.infrastructure.security.TenantContext;
import com.ctip.interfaces.rest.dto.sync.SyncDeltaDto;
import com.ctip.interfaces.rest.dto.sync.SyncManifestDto;
import com.ctip.interfaces.rest.error.ApiException;
import com.ctip.interfaces.rest.mapper.SyncDtoMapper;
import com.ctip.interfaces.rest.openapi.SyncApi;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 增量同步端點(docs/spec/09-api.md §9.1「同步」、11 §11.5)。
 *
 * <p>租戶範圍一律取自 {@link TenantContext}:{@code scope=TENANT} 指的是「呼叫者自己的租戶」,
 * 沒有「同步別人的 Bloom」這件事——呼叫端不得指定 tenantId。
 *
 * <p>{@code POST /api/v1/sync/check} <strong>不存在</strong>(§9.1:與
 * {@code POST /api/v1/iocs/lookup} 功能完全相同,已於 v2.0 移除)。Bloom 命中後的精確驗證走後者。
 */
@RestController
@RequestMapping("/api/v1/sync")
class SyncController implements SyncApi {

    private final SyncService sync;
    private final TenantContext tenantContext;
    private final SyncDtoMapper mapper;
    private final HttpServletRequest request;

    SyncController(SyncService sync, TenantContext tenantContext, SyncDtoMapper mapper, HttpServletRequest request) {
        this.sync = sync;
        this.tenantContext = tenantContext;
        this.mapper = mapper;
        this.request = request;
    }

    @Override
    @GetMapping("/manifest")
    @PreAuthorize("hasAuthority('sync:bloom')")
    public SyncManifestDto manifest() {
        return mapper.toDto(sync.manifest(tenantContext.tenantId()));
    }

    @Override
    @GetMapping("/bloom")
    @PreAuthorize("hasAuthority('sync:bloom')")
    public ResponseEntity<byte[]> bloom(@RequestParam(defaultValue = "PUBLIC") BloomScope scope) {
        BloomDownload download =
                sync.download(scope, tenantContext.tenantId(), subject()).orElseThrow(ApiException::notFound);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                // URL 不含版本而 artifact 每日重建,中介快取會發出舊版本(client 驗 checksum
                // 後會丟棄重下,徒然多傳 18MB)。§11.2 說的「public bloom 可放 CDN」指的是把
                // artifact 檔案本身發佈上去,不是快取這個需要授權的端點
                .cacheControl(CacheControl.noStore())
                .headers(headers -> bloomHeaders(headers, download.version()))
                .body(download.content());
    }

    @Override
    @GetMapping("/delta")
    @PreAuthorize("hasAuthority('sync:delta')")
    public SyncDeltaDto delta(@RequestParam long base, @RequestParam(defaultValue = "PUBLIC") BloomScope scope) {
        return mapper.toDto(sync.delta(scope, tenantContext.tenantId(), base, subject()));
    }

    /**
     * artifact 自己的版本與參數。
     *
     * <p>manifest 的 {@code bloomVersion} 是「delta 可以到達的最新版本」,而這個回應體是
     * <strong>full snapshot</strong>(bloomVersion 0)。client 若把本地版本記成 manifest 的版號,
     * 它的陣列會少掉那些 delta 的位元,而 Bloom 的 false negative 是不可接受的錯誤
     * ——因此下載回應必須自己說明它是哪一版(client 契約第 5 條的自我驗證是第二道防線)。
     */
    private static void bloomHeaders(HttpHeaders headers, BloomVersion version) {
        headers.set("X-Bloom-Scope", version.scope().name());
        headers.set("X-Bloom-Dataset-Version", Long.toString(version.datasetVersion()));
        headers.set("X-Bloom-Version", Long.toString(version.bloomVersion()));
        headers.set("X-Bloom-Checksum", version.arrayChecksum().hex());
        headers.set("X-Bloom-Compression", version.artifact().compression().name());
        headers.set("X-Bloom-Bit-Size", Long.toString(version.parameters().bitSize()));
        headers.set("X-Bloom-Hash-Count", Integer.toString(version.parameters().hashFunctionCount()));
    }

    /** 節流的記帳對象:API key → 使用者 → 匿名 IP(§10.7 的維度順序)。 */
    private String subject() {
        return ClientSubject.of(tenantContext, request);
    }
}
