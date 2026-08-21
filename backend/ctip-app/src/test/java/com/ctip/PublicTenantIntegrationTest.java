package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** DoD M1-17:public system tenant 存在且不可刪除(不變量 T2,docs/spec/02-ddd-model.md)。 */
class PublicTenantIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PUBLIC_TENANT_ID = "00000000-0000-0000-0000-000000000000";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void publicTenantExistsWithFixedIdentity() {
        Map<String, Object> tenant =
                jdbc.queryForMap("SELECT slug, name, type, status FROM tenants WHERE id = ?::uuid", PUBLIC_TENANT_ID);
        assertThat(tenant)
                .containsEntry("slug", "public")
                .containsEntry("name", "Public")
                .containsEntry("type", "SYSTEM")
                .containsEntry("status", "ACTIVE");
    }

    @Test
    void publicTenantCannotBeDeleted() {
        assertThatThrownBy(() -> jdbc.update("DELETE FROM tenants WHERE id = ?::uuid", PUBLIC_TENANT_ID))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("cannot be deleted");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM tenants WHERE id = ?::uuid", Integer.class, PUBLIC_TENANT_ID))
                .isEqualTo(1);
    }

    @Test
    void publicTenantCannotBeRenamedOrRetyped() {
        assertThatThrownBy(() ->
                        jdbc.update("UPDATE tenants SET slug = 'not-public' WHERE id = ?::uuid", PUBLIC_TENANT_ID))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() ->
                        jdbc.update("UPDATE tenants SET type = 'ENTERPRISE' WHERE id = ?::uuid", PUBLIC_TENANT_ID))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void onlyOneSystemTenantExists() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tenants WHERE type = 'SYSTEM'", Integer.class))
                .isEqualTo(1);
    }
}
