package com.ctip.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.UserId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

/**
 * §10.3 要求的集中 PermissionEvaluator。這個測試把它的語意鎖住 —— 特別是
 * 「2 參數重載把任何 UUID 當成 tenantId」這個陷阱(ADR 0013):Phase 14 若把 indicator 的 id
 * 傳進去,會變成全員 403 的靜默 bug,而不是編譯錯誤。
 */
@Tag("unit")
class CtipPermissionEvaluatorTest {

    private static final TenantId TENANT = new TenantId(new UUID(0, 41));
    private static final TenantId OTHER = new TenantId(new UUID(0, 42));
    private static final UserId USER = new UserId(new UUID(0, 43));

    private final CtipPermissionEvaluator evaluator = new CtipPermissionEvaluator();

    /** 權限先驗:即使租戶相符,沒有該 authority 一律 false。 */
    @Test
    void authorityIsCheckedBeforeTenant() {
        Authentication auth = authentication(RoleCode.TENANT_ADMIN, Set.of("user:manage"));
        assertThat(evaluator.hasPermission(auth, TENANT.value(), "Tenant", "user:manage"))
                .isTrue();
        assertThat(evaluator.hasPermission(auth, TENANT.value(), "Tenant", "system:admin"))
                .isFalse();
    }

    /** 租戶不符即拒絕;SYSTEM_ADMIN 不受租戶限制(roles.tenant_scoped = false)。 */
    @Test
    void tenantScopeIsEnforcedExceptForSystemAdmin() {
        Authentication tenantAdmin = authentication(RoleCode.TENANT_ADMIN, Set.of("user:manage"));
        assertThat(evaluator.hasPermission(tenantAdmin, OTHER.value(), "Tenant", "user:manage"))
                .isFalse();

        Authentication systemAdmin = authentication(RoleCode.SYSTEM_ADMIN, Set.of("user:manage"));
        assertThat(evaluator.hasPermission(systemAdmin, OTHER.value(), "Tenant", "user:manage"))
                .isTrue();
    }

    /** 陷阱:2 參數重載的 UUID 一律當 tenantId,非租戶目標會恆為 false。 */
    @Test
    void twoArgOverloadTreatsAnyUuidAsATenantId() {
        Authentication auth = authentication(RoleCode.TENANT_ADMIN, Set.of("ioc:report-fp"));
        UUID someIndicatorId = new UUID(0, 99);

        assertThat(evaluator.hasPermission(auth, someIndicatorId, "ioc:report-fp"))
                .as("非 tenantId 的 UUID 必須改用 4 參數重載,否則權限判定永遠不通過")
                .isFalse();
        assertThat(evaluator.hasPermission(auth, TENANT.value(), "ioc:report-fp"))
                .isTrue();
    }

    /** 非 UUID 目標退化為單純 authority 判斷;匿名(principal 為 null)不具租戶身分。 */
    @Test
    void nonUuidTargetFallsBackToAuthorityAndAnonymousHasNoTenant() {
        Authentication auth = authentication(RoleCode.USER, Set.of("ioc:read"));
        assertThat(evaluator.hasPermission(auth, "not-a-uuid", "ioc:read")).isTrue();
        assertThat(evaluator.hasPermission(auth, "not-a-uuid", "ioc:submit")).isFalse();

        Authentication anonymous =
                new CtipAuthenticationToken(null, CtipAuthorities.of(RoleCode.ANONYMOUS, Set.of("ioc:read")), false);
        assertThat(evaluator.hasPermission(anonymous, TENANT.value(), "Tenant", "ioc:read"))
                .isFalse();
        assertThat(evaluator.hasPermission(null, TENANT.value(), "ioc:read")).isFalse();
    }

    private static Authentication authentication(RoleCode role, Set<String> permissions) {
        AuthenticatedIdentity identity = AuthenticatedIdentity.ofUser(USER, TENANT, role, permissions);
        return new CtipAuthenticationToken(identity, CtipAuthorities.of(identity), true);
    }
}
