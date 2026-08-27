package com.ctip.testing;

import com.ctip.application.port.RolePermissionRepository;
import com.ctip.application.rbac.RoleCode;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 測試用 RBAC 參考資料:預設對應 §10.3 矩陣中與認證流程相關的子集。 */
public final class StubRolePermissions implements RolePermissionRepository {

    private final Map<RoleCode, Set<String>> permissions = new EnumMap<>(RoleCode.class);

    public StubRolePermissions() {
        permissions.put(RoleCode.ANONYMOUS, Set.of("ioc:read"));
        permissions.put(RoleCode.USER, Set.of("ioc:read", "ioc:export", "apikey:create", "apikey:revoke"));
        permissions.put(
                RoleCode.PREMIUM_USER,
                Set.of("ioc:read", "ioc:export", "apikey:create", "apikey:revoke", "ioc:submit"));
        permissions.put(
                RoleCode.TENANT_ADMIN,
                Set.of("ioc:read", "ioc:export", "apikey:create", "apikey:revoke", "ioc:submit", "user:manage"));
        permissions.put(RoleCode.SYSTEM_ADMIN, allCodes());
    }

    @Override
    public Set<String> permissionsOf(RoleCode role) {
        return permissions.getOrDefault(role, Set.of());
    }

    @Override
    public Set<String> allPermissionCodes() {
        return allCodes();
    }

    private Set<String> allCodes() {
        Set<String> all = new HashSet<>(
                Set.of("ioc:read", "ioc:export", "apikey:create", "apikey:revoke", "ioc:submit", "user:manage"));
        all.addAll(Set.of("ioc:publish", "system:admin"));
        return Set.copyOf(all);
    }
}
