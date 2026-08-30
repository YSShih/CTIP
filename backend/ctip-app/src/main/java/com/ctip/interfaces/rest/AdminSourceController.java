package com.ctip.interfaces.rest;

import com.ctip.application.admin.SourceAdminService;
import com.ctip.domain.source.SourceId;
import com.ctip.interfaces.rest.dto.admin.SourceAdminDto;
import com.ctip.interfaces.rest.dto.admin.SourcePatchRequest;
import com.ctip.interfaces.rest.dto.admin.SourceSyncResultDto;
import com.ctip.interfaces.rest.mapper.AdminDtoMapper;
import com.ctip.interfaces.rest.openapi.AdminSourceApi;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 來源管理(docs/spec/09-api.md §9.1「管理」)。
 * 手動同步需 {@code source:sync},設定變更需 {@code source:manage}——兩者是不同的權限,
 * §10.3 的矩陣把它們分開列。
 */
@RestController
@RequestMapping("/api/v1/admin/sources")
class AdminSourceController implements AdminSourceApi {

    private final SourceAdminService sources;
    private final AdminDtoMapper mapper;

    AdminSourceController(SourceAdminService sources, AdminDtoMapper mapper) {
        this.sources = sources;
        this.mapper = mapper;
    }

    @Override
    @PostMapping("/{id}/sync")
    @PreAuthorize("hasAuthority('source:sync')")
    public SourceSyncResultDto syncNow(@PathVariable UUID id) {
        return mapper.toDto(sources.syncNow(new SourceId(id)));
    }

    @Override
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('source:manage')")
    public SourceAdminDto updateSource(@PathVariable UUID id, @Valid @RequestBody SourcePatchRequest request) {
        return mapper.toDto(sources.setEnabled(new SourceId(id), request.enabled()));
    }
}
