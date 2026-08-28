package com.ctip.application.identity;

import com.ctip.application.port.TenantMembershipRepository;
import com.ctip.application.port.UserRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.User;
import com.ctip.domain.user.UserId;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 「這個帳號現在還能不能持有一個 session」的單一判定點。
 *
 * <p>Phase 13 收尾稽核發現三條認證路徑對停權與成員資格的處理不一致(ADR 0013):
 * 登入會擋 {@code UserStatus != ACTIVE},但 refresh 輪替與 API key 驗證完全不看使用者狀態,
 * 而成員資格查不到時兩邊都 {@code orElse(RoleCode.USER)} —— 移除成員資格只會靜默降級成 USER,
 * 不會撤銷存取。停權在 M3 的使用者管理端點出現之前是唯一的事故處置手段,不生效等於沒有。
 *
 * <p>規則統一為:<strong>非 ACTIVE、或在該租戶沒有成員資格,就沒有身分</strong>(fail-closed)。
 */
@Service
public class AccountAccessPolicy {

    private final UserRepository users;
    private final TenantMembershipRepository memberships;

    public AccountAccessPolicy(UserRepository users, TenantMembershipRepository memberships) {
        this.users = users;
        this.memberships = memberships;
    }

    /** 不論狀態的查詢。只給登出用——被停權的使用者仍應能撤銷自己的 token family。 */
    public Optional<User> find(UserId id) {
        return users.findById(id);
    }

    /** 使用者存在且狀態為 {@code ACTIVE}。 */
    public Optional<User> activeUser(UserId id) {
        return users.findById(id).filter(User::isActive);
    }

    /** 使用者在該租戶的角色。查無成員資格即回 empty,<strong>不得退回預設角色</strong>。 */
    public Optional<RoleCode> roleOf(TenantId tenantId, UserId userId) {
        return memberships.roleOf(tenantId, userId);
    }

    /** 仍為 ACTIVE 且在該租戶具成員資格時的角色。 */
    public Optional<RoleCode> eligibleRole(User user, TenantId tenantId) {
        return user.isActive() ? roleOf(tenantId, user.id()) : Optional.empty();
    }
}
