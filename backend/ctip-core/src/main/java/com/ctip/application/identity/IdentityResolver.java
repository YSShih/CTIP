package com.ctip.application.identity;

import com.ctip.application.port.RolePermissionRepository;
import com.ctip.application.port.TenantMembershipRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.User;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 由「使用者 × 租戶」解析出角色與權限集合(docs/spec/10-identity-plans.md §10.3)。
 * 角色→權限對應存於資料庫,此處不硬編任何一格。
 */
@Service
public class IdentityResolver {

    private final TenantMembershipRepository memberships;
    private final RolePermissionRepository rolePermissions;

    public IdentityResolver(TenantMembershipRepository memberships, RolePermissionRepository rolePermissions) {
        this.memberships = memberships;
        this.rolePermissions = rolePermissions;
    }

    public AuthenticatedIdentity resolve(User user) {
        return resolve(user, user.primaryTenantId());
    }

    public AuthenticatedIdentity resolve(User user, TenantId tenantId) {
        RoleCode role = roleOf(tenantId, user);
        return AuthenticatedIdentity.ofUser(user.id(), tenantId, role, permissionsOf(role));
    }

    public RoleCode roleOf(TenantId tenantId, User user) {
        return memberships.roleOf(tenantId, user.id()).orElse(RoleCode.USER);
    }

    public Set<String> permissionsOf(RoleCode role) {
        return rolePermissions.permissionsOf(role);
    }

    /** 匿名身分:綁 public tenant,權限取 ANONYMOUS 角色在資料庫中的設定。 */
    public Set<String> anonymousPermissions() {
        return rolePermissions.permissionsOf(RoleCode.ANONYMOUS);
    }
}
