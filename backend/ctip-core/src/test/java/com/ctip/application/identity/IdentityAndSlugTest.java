package com.ctip.application.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.UserId;
import com.ctip.testing.InMemoryTenantMemberships;
import com.ctip.testing.StubRolePermissions;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** 身分解析、slug 導出與相關值物件的行為(§10.1、§10.3)。 */
@Tag("unit")
class IdentityAndSlugTest {

    private static final TenantId TENANT = new TenantId(new UUID(0, 31));
    private static final UserId USER = new UserId(new UUID(0, 32));

    @Test
    void slugSanitisationProducesValidSlugs() {
        assertThat(TenantSlugs.sanitize("Example Org, Inc.")).isEqualTo("example-org-inc");
        assertThat(TenantSlugs.sanitize("---")).isEmpty();
        assertThat(TenantSlugs.sanitize(null)).isEmpty();
        assertThat(TenantSlugs.sanitize("a".repeat(80))).hasSize(40);
        assertThat(TenantSlugs.withSuffix("", new UUID(0x5eedL, 1)).value()).startsWith("t-");
        assertThat(TenantSlugs.withSuffix("acme", new UUID(0x5eedL, 1)).value()).startsWith("acme-");
    }

    @Test
    void identityResolverFallsBackToUserWhenNoMembershipExists() {
        InMemoryTenantMemberships memberships = new InMemoryTenantMemberships();
        StubRolePermissions rolePermissions = new StubRolePermissions();
        IdentityResolver resolver = new IdentityResolver(memberships, rolePermissions);

        assertThat(resolver.permissionsOf(RoleCode.ANONYMOUS)).containsExactly("ioc:read");
        assertThat(resolver.anonymousPermissions()).containsExactly("ioc:read");

        memberships.assign(TENANT, USER, RoleCode.PREMIUM_USER);
        assertThat(memberships.roleOf(TENANT, USER)).contains(RoleCode.PREMIUM_USER);
    }

    @Test
    void authenticatedIdentityExposesPermissionsAndApiKeyFlag() {
        AuthenticatedIdentity user = AuthenticatedIdentity.ofUser(USER, TENANT, RoleCode.USER, Set.of("ioc:read"));
        assertThat(user.hasPermission("ioc:read")).isTrue();
        assertThat(user.hasPermission("ioc:submit")).isFalse();
        assertThat(user.isApiKey()).isFalse();
    }

    @Test
    void clientInfoTruncatesOverlongUserAgents() {
        String longAgent = "u".repeat(600);
        assertThat(new ClientInfo(longAgent, "127.0.0.1").userAgent()).hasSize(512);
        assertThat(ClientInfo.unknown().ip()).isNull();
    }

    @Test
    void roleCodeMarksSystemAdminAsCrossTenant() {
        assertThat(RoleCode.SYSTEM_ADMIN.isTenantScoped()).isFalse();
        assertThat(RoleCode.TENANT_ADMIN.isTenantScoped()).isTrue();
    }

    @Test
    void loginAndRotationResultsCarryTheirOutcome() {
        assertThat(LoginResult.failed(LoginFailure.LOCKED).isSuccess()).isFalse();
        assertThat(RotatedTokens.failed(com.ctip.domain.user.RefreshTokenRotationOutcome.INVALID)
                        .isRotated())
                .isFalse();
    }
}
