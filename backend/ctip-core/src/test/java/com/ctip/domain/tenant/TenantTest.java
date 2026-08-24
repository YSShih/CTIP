package com.ctip.domain.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** 不變量 T1–T4(docs/spec/02-ddd-model.md §2.3)。 */
@Tag("unit")
class TenantTest {

    private static final TenantId DEMO_ID = new TenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Test
    void t1SlugMustMatchFormat() {
        assertThat(new TenantSlug("demo-org").value()).isEqualTo("demo-org");
        assertThatThrownBy(() -> new TenantSlug("Demo")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TenantSlug("-bad")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TenantSlug("a")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TenantSlug(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void t2PublicTenantCannotBeRenamedOrSuspendedOrRetyped() {
        Tenant publicTenant = Tenant.reconstitute(
                TenantId.PUBLIC, new TenantSlug("public"), "Public", TenantType.SYSTEM, TenantStatus.ACTIVE);
        assertThatThrownBy(() -> publicTenant.rename("Hacked")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(publicTenant::suspend).isInstanceOf(IllegalStateException.class);
        // 變更 type:public tenant 以非 SYSTEM 型別重建即拒絕
        assertThatThrownBy(() -> Tenant.reconstitute(
                        TenantId.PUBLIC,
                        new TenantSlug("public"),
                        "Public",
                        TenantType.ENTERPRISE,
                        TenantStatus.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void t4OnlyPublicTenantMayBeSystemType() {
        assertThatThrownBy(() -> Tenant.create(DEMO_ID, new TenantSlug("demo"), "Demo", TenantType.SYSTEM))
                .isInstanceOf(IllegalArgumentException.class);
        Tenant demo = Tenant.create(DEMO_ID, new TenantSlug("demo"), "Demo", TenantType.ORGANIZATION);
        assertThat(demo.isPublic()).isFalse();
    }

    @Test
    void nonPublicTenantCanRenameAndSuspend() {
        Tenant demo = Tenant.create(DEMO_ID, new TenantSlug("demo"), "Demo", TenantType.ORGANIZATION);
        demo.rename("Demo Organization");
        assertThat(demo.name()).isEqualTo("Demo Organization");
        demo.suspend();
        assertThat(demo.status()).isEqualTo(TenantStatus.SUSPENDED);
        assertThatThrownBy(() -> demo.rename(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publicTenantConstantMatchesSeededUuid() {
        assertThat(TenantId.PUBLIC.value()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000000"));
        assertThat(TenantId.PUBLIC.isPublic()).isTrue();
        assertThat(DEMO_ID.isPublic()).isFalse();
    }
}
