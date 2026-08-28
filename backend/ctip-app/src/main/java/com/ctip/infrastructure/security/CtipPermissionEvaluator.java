package com.ctip.infrastructure.security;

import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.application.rbac.RoleCode;
import java.io.Serializable;
import java.util.UUID;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;

/**
 * 集中的權限判斷(docs/spec/10-identity-plans.md §10.3):
 * {@code @PreAuthorize("hasPermission(#tenantId, 'Tenant', 'user:manage')")}。
 *
 * <p>tenant-scoped 權限同時要求「持有該權限」與「目標屬於自己的租戶」;
 * {@code SYSTEM_ADMIN}(roles.tenant_scoped = false)不受租戶限制。
 * 非 tenant-scoped 的檢查退化為單純的 authority 判斷,不得在 controller 散落 role 判斷。
 *
 * <p><strong>陷阱:</strong>2 參數的 {@code hasPermission(#target, 'perm')} 只要 {@code target}
 * 是 {@code UUID} 就<em>一律解讀為 tenantId</em>。若寫成
 * {@code @PreAuthorize("hasPermission(#id, 'ioc:report-fp')")} 而 {@code #id} 是 indicator 的 UUID,
 * 會拿 indicatorId 去比 tenantId → 對所有人恆為 false,變成全員 403 的靜默 bug。
 * 非租戶目標必須用 4 參數重載並給對應的 {@code targetType}(ADR 0013)。
 */
public class CtipPermissionEvaluator implements PermissionEvaluator {

    private static final String TENANT_TARGET = "Tenant";

    @Override
    public boolean hasPermission(Authentication authentication, Object target, Object permission) {
        if (target instanceof UUID tenantId) {
            return hasPermission(authentication, tenantId, TENANT_TARGET, permission);
        }
        return holdsAuthority(authentication, permission);
    }

    @Override
    public boolean hasPermission(
            Authentication authentication, Serializable targetId, String targetType, Object permission) {
        if (!holdsAuthority(authentication, permission)) {
            return false;
        }
        if (!TENANT_TARGET.equals(targetType)) {
            return true;
        }
        return matchesTenant(authentication, targetId);
    }

    private static boolean holdsAuthority(Authentication authentication, Object permission) {
        if (authentication == null || permission == null) {
            return false;
        }
        String required = permission.toString();
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> required.equals(authority.getAuthority()));
    }

    private static boolean matchesTenant(Authentication authentication, Serializable targetId) {
        if (!(authentication.getPrincipal() instanceof AuthenticatedIdentity identity)) {
            return false;
        }
        if (identity.role() == RoleCode.SYSTEM_ADMIN) {
            return true;
        }
        return targetId != null && identity.tenantId().value().toString().equals(targetId.toString());
    }
}
