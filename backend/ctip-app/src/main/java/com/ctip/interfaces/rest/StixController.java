package com.ctip.interfaces.rest;

import com.ctip.application.stix.StixExportService;
import com.ctip.application.stix.StixQueryService;
import com.ctip.infrastructure.security.AuthState;
import com.ctip.infrastructure.security.TenantContext;
import com.ctip.interfaces.rest.error.ApiException;
import com.ctip.interfaces.rest.error.ErrorCode;
import com.ctip.interfaces.rest.openapi.StixApi;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * STIX 端點(docs/spec/09-api.md §9.1):GET /{stixId} 匿名;GET /bundle 需 stix:export——
 * 匿名無此權限(10 §10.6 匿名 bundle 匯出 ✗),M1 以 AuthState 判定(RBAC 是 Phase 13;ADR 0005)。
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

    StixController(
            StixQueryService query,
            StixExportService export,
            StixBundleWriter bundleWriter,
            TenantContext tenantContext) {
        this.query = query;
        this.export = export;
        this.bundleWriter = bundleWriter;
        this.tenantContext = tenantContext;
    }

    @Override
    @GetMapping(value = "/bundle", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> bundle() {
        if (tenantContext.authState() != AuthState.AUTHENTICATED) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Bundle export requires stix:export");
        }
        String json = bundleWriter.toJson(export.exportBundle(tenantContext.visibility()));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
    }

    @Override
    @GetMapping(value = "/{stixId}", produces = MediaType.APPLICATION_JSON_VALUE)
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
