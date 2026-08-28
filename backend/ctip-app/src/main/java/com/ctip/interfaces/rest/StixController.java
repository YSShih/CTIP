package com.ctip.interfaces.rest;

import com.ctip.application.plan.QuotaService;
import com.ctip.application.stix.StixExportService;
import com.ctip.application.stix.StixQueryService;
import com.ctip.infrastructure.security.TenantContext;
import com.ctip.interfaces.rest.error.ApiException;
import com.ctip.interfaces.rest.openapi.StixApi;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * STIX 端點(docs/spec/09-api.md §9.1):GET /{stixId} 需 ioc:read(匿名亦具此權限);
 * GET /bundle 需 stix:export——匿名角色無此權限(§10.3 矩陣),因此回 403。
 * 業務規則不在 controller(規則 10):可見度與再散布過濾在 application/domain 層;
 * 錯誤結構由 ApiExceptionHandler 統一(§9.4)。
 */
@RestController
@RequestMapping("/api/v1/stix")
class StixController implements StixApi {

    private final StixQueryService query;
    private final StixExportService export;
    private final StixBundleWriter bundleWriter;
    private final TenantContext tenantContext;
    private final QuotaService quotas;

    StixController(
            StixQueryService query,
            StixExportService export,
            StixBundleWriter bundleWriter,
            TenantContext tenantContext,
            QuotaService quotas) {
        this.query = query;
        this.export = export;
        this.bundleWriter = bundleWriter;
        this.tenantContext = tenantContext;
        this.quotas = quotas;
    }

    @Override
    @GetMapping(value = "/bundle", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('stix:export')")
    public ResponseEntity<String> bundle() {
        String json = bundleWriter.toJson(
                export.exportBundle(tenantContext.visibility(), quotas.stixExportLimit(tenantContext.tenantId())));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
    }

    @Override
    @GetMapping(value = "/{stixId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ioc:read')")
    public ResponseEntity<Object> byStixId(@PathVariable String stixId) {
        var marking = query.findMarking(stixId);
        if (marking.isPresent()) {
            return ResponseEntity.ok(marking.get());
        }
        return query.findIndicatorContent(stixId, tenantContext.visibility())
                .<ResponseEntity<Object>>map(json -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(json))
                .orElseThrow(ApiException::notFound);
    }
}
