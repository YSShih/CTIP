package com.ctip.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.shared.Visibility;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.UserId;
import com.ctip.sdk.Tlp;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 安全層的請求上下文(docs/spec/01-architecture.md §1.11):匿名綁 public tenant、可見度對應表;
 * Phase 13 起額外承載完整身分,但 AuthState 兩態與 TLP 可見度規則不變。
 */
@Tag("unit")
class TenantContextTest {

    private static final TenantId DEMO = new TenantId(new UUID(0, 1));

    private static AuthenticatedIdentity demoIdentity() {
        return AuthenticatedIdentity.ofUser(
                new UserId(new UUID(0, 2)), DEMO, RoleCode.USER, Set.of("ioc:read", "ioc:export"));
    }

    @Test
    void anonymousBindingYieldsPublicTenantAndClearOnlyVisibility() {
        TenantContext context = new TenantContext();
        context.bindAnonymous();
        assertThat(context.tenantId()).isEqualTo(TenantId.PUBLIC);
        assertThat(context.authState()).isEqualTo(AuthState.ANONYMOUS);
        assertThat(context.identity()).isEmpty();
        Visibility visibility = context.visibility();
        assertThat(visibility.viewerTenantId()).isEqualTo(TenantId.PUBLIC);
        assertThat(visibility.maxPublicTlp()).isEqualTo(Tlp.CLEAR);
    }

    @Test
    void authenticatedBindingYieldsOwnTenantAndGreenPublicVisibility() {
        TenantContext context = new TenantContext();
        context.bindAuthenticated(demoIdentity());
        assertThat(context.authState()).isEqualTo(AuthState.AUTHENTICATED);
        assertThat(context.requireIdentity().role()).isEqualTo(RoleCode.USER);
        assertThat(context.requireIdentity().hasPermission("ioc:export")).isTrue();
        Visibility visibility = context.visibility();
        assertThat(visibility.viewerTenantId()).isEqualTo(DEMO);
        assertThat(visibility.maxPublicTlp()).isEqualTo(Tlp.GREEN);
    }

    @Test
    void rebindingToAnonymousClearsTheIdentity() {
        TenantContext context = new TenantContext();
        context.bindAuthenticated(demoIdentity());
        context.bindAnonymous();
        assertThat(context.authState()).isEqualTo(AuthState.ANONYMOUS);
        assertThat(context.tenantId()).isEqualTo(TenantId.PUBLIC);
        assertThatThrownBy(context::requireIdentity).isInstanceOf(IllegalStateException.class);
    }
}
