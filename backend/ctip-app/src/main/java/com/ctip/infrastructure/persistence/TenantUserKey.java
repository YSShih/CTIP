package com.ctip.infrastructure.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** tenant_users 的複合主鍵 (tenant_id, user_id)(表 14)。 */
class TenantUserKey implements Serializable {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    UUID tenantId;
    UUID userId;

    TenantUserKey() {}

    TenantUserKey(UUID tenantId, UUID userId) {
        this.tenantId = tenantId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TenantUserKey key)) {
            return false;
        }
        return Objects.equals(tenantId, key.tenantId) && Objects.equals(userId, key.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, userId);
    }
}
