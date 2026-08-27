package com.ctip.infrastructure.persistence;

import com.ctip.application.port.TenantMembershipRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.UserId;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** tenant_users 的讀寫。角色以 code 對應 roles 表的代理鍵。 */
@Repository
@Transactional
class TenantMembershipRepositoryAdapter implements TenantMembershipRepository {

    private final TenantUserJpaRepository memberships;
    private final RoleJpaRepository roles;

    TenantMembershipRepositoryAdapter(TenantUserJpaRepository memberships, RoleJpaRepository roles) {
        this.memberships = memberships;
        this.roles = roles;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RoleCode> roleOf(TenantId tenantId, UserId userId) {
        return memberships
                .findByTenantIdAndUserId(tenantId.value(), userId.value())
                .flatMap(membership -> roles.findById(membership.roleId))
                .map(role -> RoleCode.valueOf(role.code));
    }

    @Override
    public void assign(TenantId tenantId, UserId userId, RoleCode role) {
        RoleEntity roleEntity =
                roles.findByCode(role.name()).orElseThrow(() -> new IllegalStateException("角色不存在(V24 種子未套用?):" + role));
        TenantUserEntity entity = memberships
                .findByTenantIdAndUserId(tenantId.value(), userId.value())
                .orElseGet(TenantUserEntity::new);
        entity.tenantId = tenantId.value();
        entity.userId = userId.value();
        entity.roleId = roleEntity.id;
        memberships.save(entity);
    }
}
