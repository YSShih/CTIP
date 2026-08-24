package com.ctip.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.domain.shared.Visibility;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.Tlp;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** M1 最小安全層(docs/spec/01-architecture.md §1.11):匿名綁 public tenant、可見度對應表。 */
@Tag("unit")
class TenantContextTest {

    @Test
    void anonymousBindingYieldsPublicTenantAndClearOnlyVisibility() {
        TenantContext context = new TenantContext();
        context.bindAnonymous();
        assertThat(context.tenantId()).isEqualTo(TenantId.PUBLIC);
        assertThat(context.authState()).isEqualTo(AuthState.ANONYMOUS);
        Visibility visibility = context.visibility();
        assertThat(visibility.viewerTenantId()).isEqualTo(TenantId.PUBLIC);
        assertThat(visibility.maxPublicTlp()).isEqualTo(Tlp.CLEAR);
    }

    @Test
    void authenticatedBindingYieldsOwnTenantAndGreenPublicVisibility() {
        TenantContext context = new TenantContext();
        TenantId demo = new TenantId(new UUID(0, 1));
        context.bindAuthenticated(demo);
        assertThat(context.authState()).isEqualTo(AuthState.AUTHENTICATED);
        Visibility visibility = context.visibility();
        assertThat(visibility.viewerTenantId()).isEqualTo(demo);
        assertThat(visibility.maxPublicTlp()).isEqualTo(Tlp.GREEN);
    }

    @Test
    void filterBindsAnonymousAndContinuesChain() throws Exception {
        TenantContext context = new TenantContext();
        TenantId demo = new TenantId(new UUID(0, 1));
        context.bindAuthenticated(demo);

        MockFilterChain chain = new MockFilterChain();
        new AnonymousTenantFilter(context).doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(context.authState()).isEqualTo(AuthState.ANONYMOUS);
        assertThat(context.tenantId()).isEqualTo(TenantId.PUBLIC);
        assertThat(chain.getRequest()).isNotNull();
    }
}
