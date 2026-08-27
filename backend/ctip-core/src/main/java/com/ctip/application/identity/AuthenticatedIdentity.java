package com.ctip.application.identity;

import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.identity.ApiKeyId;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.UserId;
import java.util.Set;

/**
 * 已認證身分(docs/spec/phases/phase-13.md:AuthState 擴充為完整身分)。
 *
 * <p>{@code AuthState} 列舉維持不變——它是 TLP 可見度的軸,Phase 13 明令不得改動
 * Phase 4 建立的 TLP 過濾邏輯;身分細節改由本 record 承載(ADR 0012 決策 3)。
 * {@code apiKeyId} 僅在以 API key 認證時非 null,供限流維度 1 使用(Phase 14)。
 */
public record AuthenticatedIdentity(
        UserId userId, TenantId tenantId, RoleCode role, Set<String> permissions, ApiKeyId apiKeyId) {

    public AuthenticatedIdentity {
        permissions = Set.copyOf(permissions);
    }

    public static AuthenticatedIdentity ofUser(
            UserId userId, TenantId tenantId, RoleCode role, Set<String> permissions) {
        return new AuthenticatedIdentity(userId, tenantId, role, permissions, null);
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    public boolean isApiKey() {
        return apiKeyId != null;
    }
}
