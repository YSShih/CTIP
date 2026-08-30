package com.ctip.interfaces.rest;

import com.ctip.application.admin.DataSubjectService;
import com.ctip.domain.user.UserId;
import com.ctip.interfaces.rest.dto.admin.DataSubjectErasureDto;
import com.ctip.interfaces.rest.dto.admin.DataSubjectReportDto;
import com.ctip.interfaces.rest.mapper.AdminDtoMapper;
import com.ctip.interfaces.rest.openapi.AdminDataSubjectApi;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 資料主體的查詢與刪除(docs/spec/13-platform-ops.md §13.4:
 * 「提供資料主體查詢與刪除的操作程序(M3 提供管理端點)」)。
 *
 * <p>以 {@code userId} 定位而不是 email 或 IP:個資不得出現在 URL(§13.3 的日誌考量——
 * 路徑會進反向代理與存取日誌)。操作程序見 {@code docs/deployment/privacy.md}。
 */
@RestController
@RequestMapping("/api/v1/admin/data-subjects")
class AdminDataSubjectController implements AdminDataSubjectApi {

    private final DataSubjectService dataSubjects;
    private final AdminDtoMapper mapper;

    AdminDataSubjectController(DataSubjectService dataSubjects, AdminDtoMapper mapper) {
        this.dataSubjects = dataSubjects;
        this.mapper = mapper;
    }

    @Override
    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('system:admin')")
    public DataSubjectReportDto report(@PathVariable UUID userId) {
        return mapper.toDto(dataSubjects.report(new UserId(userId)));
    }

    @Override
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('system:admin')")
    public DataSubjectErasureDto erase(@PathVariable UUID userId) {
        return mapper.toDto(dataSubjects.erase(new UserId(userId)));
    }
}
