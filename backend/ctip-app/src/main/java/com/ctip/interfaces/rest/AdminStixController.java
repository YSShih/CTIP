package com.ctip.interfaces.rest;

import com.ctip.application.admin.StixRebuildService;
import com.ctip.interfaces.rest.dto.admin.StixRebuildResultDto;
import com.ctip.interfaces.rest.openapi.AdminStixApi;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * STIX 投影重建(docs/spec/09-api.md §9.1「管理」)。
 * {@code stix_objects} 是衍生資料(§7.8.6),隨時可由 indicators 重算。
 */
@RestController
@RequestMapping("/api/v1/admin/stix")
class AdminStixController implements AdminStixApi {

    private final StixRebuildService rebuild;

    AdminStixController(StixRebuildService rebuild) {
        this.rebuild = rebuild;
    }

    @Override
    @PostMapping("/rebuild")
    @PreAuthorize("hasAuthority('system:admin')")
    public StixRebuildResultDto rebuildStix() {
        return new StixRebuildResultDto(rebuild.rebuildAll());
    }
}
