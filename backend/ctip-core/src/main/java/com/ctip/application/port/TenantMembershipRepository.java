package com.ctip.application.port;

import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.UserId;
import java.util.Optional;

/**
 * tenant_users 關聯(兩模型)。PK 為 (tenant_id, user_id),故一個使用者在一個租戶內
 * 恰有一個角色(docs/spec/04-data-dictionary.md 表 14)。
 */
public interface TenantMembershipRepository {

    Optional<RoleCode> roleOf(TenantId tenantId, UserId userId);

    void assign(TenantId tenantId, UserId userId, RoleCode role);
}
