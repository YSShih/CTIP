package com.ctip.interfaces.rest;

import com.ctip.application.admin.SubscriptionAdminService;
import com.ctip.application.admin.TenantAdminService;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.plan.Subscription;
import com.ctip.domain.tenant.TenantId;
import com.ctip.interfaces.rest.dto.admin.AssignPlanRequest;
import com.ctip.interfaces.rest.dto.admin.SubscriptionAssignmentDto;
import com.ctip.interfaces.rest.dto.admin.TenantOverviewDto;
import com.ctip.interfaces.rest.error.ApiException;
import com.ctip.interfaces.rest.error.ErrorCode;
import com.ctip.interfaces.rest.mapper.AdminDtoMapper;
import com.ctip.interfaces.rest.openapi.AdminTenantApi;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租戶管理(docs/spec/09-api.md §9.1「管理」)。全部端點皆需 {@code system:admin},
 * 且每一次呼叫都會留下 {@code ADMIN_ACTION} 稽核(§13.5 觸發點對照表,由 AuditAccessFilter 記錄)。
 */
@RestController
@RequestMapping("/api/v1/admin/tenants")
class AdminTenantController implements AdminTenantApi {

    /** {@code planCode} 的保留值:取消目前訂閱(B3:取消後不得回到 ACTIVE)。 */
    private static final String CANCEL = "CANCEL";

    private final TenantAdminService tenants;
    private final SubscriptionAdminService subscriptions;
    private final AdminDtoMapper mapper;

    AdminTenantController(TenantAdminService tenants, SubscriptionAdminService subscriptions, AdminDtoMapper mapper) {
        this.tenants = tenants;
        this.subscriptions = subscriptions;
        this.mapper = mapper;
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('system:admin')")
    public List<TenantOverviewDto> listTenants() {
        return tenants.list().stream().map(mapper::toDto).toList();
    }

    @Override
    @PatchMapping("/{id}/subscription")
    @PreAuthorize("hasAuthority('system:admin')")
    public SubscriptionAssignmentDto assignPlan(@PathVariable UUID id, @Valid @RequestBody AssignPlanRequest request) {
        TenantId tenantId = new TenantId(id);
        String requested = request.planCode().toUpperCase(Locale.ROOT);
        Subscription subscription = CANCEL.equals(requested)
                ? subscriptions.cancel(tenantId)
                : subscriptions.assignPlan(tenantId, planOf(requested));
        return mapper.toDto(subscription);
    }

    private static PlanCode planOf(String requested) {
        try {
            return PlanCode.valueOf(requested);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Unknown plan code: " + requested);
        }
    }
}
