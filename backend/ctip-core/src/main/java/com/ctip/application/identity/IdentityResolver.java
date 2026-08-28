package com.ctip.application.identity;

import com.ctip.application.port.RolePermissionRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.User;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 由「使用者 × 租戶」解析出角色與權限集合(docs/spec/10-identity-plans.md §10.3)。
 * 角色→權限對應存於資料庫,此處不硬編任何一格。
 *
 * <p>解析回 {@link Optional}:被停權或已無成員資格的使用者<strong>沒有身分</strong>,
 * 不再退回預設 {@code USER} 角色(ADR 0013,見 {@link AccountAccessPolicy})。
 */
@Service
public class IdentityResolver {

    private final AccountAccessPolicy accounts;
    private final RolePermissionRepository rolePermissions;

    public IdentityResolver(AccountAccessPolicy accounts, RolePermissionRepository rolePermissions) {
        this.accounts = accounts;
        this.rolePermissions = rolePermissions;
    }

    public Optional<AuthenticatedIdentity> resolve(User user) {
        return resolve(user, user.primaryTenantId());
    }

    public Optional<AuthenticatedIdentity> resolve(User user, TenantId tenantId) {
        return accounts.eligibleRole(user, tenantId)
                .map(role -> AuthenticatedIdentity.ofUser(user.id(), tenantId, role, permissionsOf(role)));
    }

    public Set<String> permissionsOf(RoleCode role) {
        return rolePermissions.permissionsOf(role);
    }

    /** 匿名身分:綁 public tenant,權限取 ANONYMOUS 角色在資料庫中的設定。 */
    public Set<String> anonymousPermissions() {
        return rolePermissions.permissionsOf(RoleCode.ANONYMOUS);
    }
}
