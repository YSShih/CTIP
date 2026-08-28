package com.ctip.testing;

import com.ctip.application.port.TenantMembershipRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.UserId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** 測試用 tenant_users:PK 為 (tenantId, userId),一人一租戶一角色。 */
public final class InMemoryTenantMemberships implements TenantMembershipRepository {

    private record Key(TenantId tenantId, UserId userId) {}

    private final Map<Key, RoleCode> roles = new LinkedHashMap<>();

    @Override
    public Optional<RoleCode> roleOf(TenantId tenantId, UserId userId) {
        return Optional.ofNullable(roles.get(new Key(tenantId, userId)));
    }

    @Override
    public void assign(TenantId tenantId, UserId userId, RoleCode role) {
        roles.put(new Key(tenantId, userId), role);
    }

    /** 移除成員資格(port 尚無此操作,使用者管理是 M3);供 fail-closed 測試使用。 */
    public void remove(TenantId tenantId, UserId userId) {
        roles.remove(new Key(tenantId, userId));
    }
}
