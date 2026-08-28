package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * DoD M1-16:Flyway 從空資料庫執行至最新版本成功(docs/spec/04-data-dictionary.md §4.7)。
 * 版本號區段:V1–V19 = M1、V20–V29 = M2、V30+ = M3;M1 只用到 V7,故中間跳號是規格的區段設計。
 */
class MigrationIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void allM1MigrationsApplyFromEmptyDatabase() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);
        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "20", "21", "24", "27");
    }

    @Test
    void allNineM1TablesExist() {
        List<String> tables =
                jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class);
        assertThat(tables)
                .contains(
                        "tenants",
                        "sources",
                        "source_sync",
                        "indicators",
                        "indicator_sources",
                        "hash_records",
                        "ingestion_rejections",
                        "stix_objects",
                        "stix_relationships");
    }

    /** Phase 13 交付表 10–16(V20/V21);RBAC 種子由 V24 寫入。 */
    @Test
    void phase13IdentityTablesExist() {
        List<String> tables =
                jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class);
        assertThat(tables)
                .contains(
                        "users",
                        "roles",
                        "permissions",
                        "role_permissions",
                        "tenant_users",
                        "refresh_tokens",
                        "api_keys");
    }

    /** 尚未進入的 phase 不得預先建表(規則 16:不留 placeholder)。 */
    @Test
    void tablesOfLaterPhasesDoNotExist() {
        List<String> tables =
                jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class);
        assertThat(tables)
                .doesNotContain(
                        "plans",
                        "subscriptions",
                        "threats",
                        "bloom_versions",
                        "webhooks",
                        "notifications",
                        "audit_logs");
    }

    @Test
    void requiredExtensionsInstalled() {
        List<String> extensions = jdbc.queryForList("SELECT extname FROM pg_extension", String.class);
        assertThat(extensions).contains("pgcrypto", "pg_trgm");
    }
}
