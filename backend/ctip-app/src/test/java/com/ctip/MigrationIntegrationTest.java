package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * DoD M1-16:Flyway 從空資料庫執行至最新版本成功(docs/spec/04-data-dictionary.md §4.7)。
 * 版本號一律遞增、依實作順序指派(§4.7;ADR 0014)。V8–V19 與 V22/V23/V25/V26 的跳號是
 * 舊區段設計的殘留:那些號碼永遠不會有檔案,已套用的 migration 不得改號(checksum)。
 */
class MigrationIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * 每一支 migration 檔都成功套用,且套用順序嚴格遞增。
     *
     * <p>原本是寫死版本清單的 {@code containsExactly}——每個新增 migration 的 phase 都會讓它變紅,
     * 而那不是缺陷、是預期中的成長。改為由 {@code db/migration} 目錄實際內容推導(ADR 0017)。
     *
     * <p><strong>本測試抓不到 ADR 0014 的 out-of-order 危害</strong>,這點必須講清楚:
     * 全新資料庫一律照版本序一次套完,所以「順序遞增」在這裡恆真——即使放進一支版本號
     * 低於既有最高版的 migration 也一樣。那個危害只在**既有**資料庫上出現
     * ({@code FlywayValidateException},ADR 0014 實測),repo 層的測試結構上看不到。
     * 真正的守門是 §4.7 的編號政策本身:新號碼必須大於現有最大值。
     */
    @Test
    void everyMigrationOnDiskIsApplied() {
        List<String> applied = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);

        assertThat(applied)
                .as("flyway_schema_history 應涵蓋 db/migration 下的每一支 migration")
                .containsExactlyInAnyOrderElementsOf(migrationVersionsOnDisk());

        assertThat(applied.stream().map(Integer::valueOf).toList())
                .as("版本號不得重複")
                .doesNotHaveDuplicates();
    }

    /**
     * 掃 <strong>classpath</strong> 上的 migration 檔名,取 {@code V<n>__} 的 n。
     *
     * <p>刻意讀 classpath 而非 {@code src/main/resources}:Flyway 讀的是 classpath,
     * 而 {@code mvn test}(未 clean)不會刪掉 {@code target/classes} 裡已從原始碼移除的檔案。
     * 讀原始碼目錄會讓兩邊看到不同的 migration 集合,測試就抓不到真正被套用的東西。
     */
    private static List<String> migrationVersionsOnDisk() {
        Path dir = classpathMigrationDir();
        Pattern version = Pattern.compile("^V(\\d+)__.*\\.sql$");
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(path -> version.matcher(path.getFileName().toString()))
                    .filter(Matcher::matches)
                    .map(matcher -> matcher.group(1))
                    .sorted(Comparator.comparingInt(Integer::valueOf))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("讀不到 migration 目錄:" + dir, e);
        }
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

    /** Phase 14 交付表 17、18、18b(V28);方案種子與 subscription:read 權限由 V29 寫入。 */
    @Test
    void phase14PlanTablesExist() {
        List<String> tables =
                jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class);
        assertThat(tables).contains("plans", "subscriptions", "import_jobs");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM plans", Integer.class))
                .as("V29 必須種入四個方案")
                .isEqualTo(4);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM permissions WHERE code = 'subscription:read'", Integer.class))
                .isEqualTo(1);
        // ingestion_rejections 的匯入關聯欄位(表 7 + 表 18b)
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM information_schema.columns"
                                + " WHERE table_name = 'ingestion_rejections' AND column_name = 'import_job_id'",
                        Integer.class))
                .isEqualTo(1);
    }

    /** Phase 15 交付表 22、23(V30);兩張表在生成第一份 bloom 之前一律為空。 */
    @Test
    void phase15BloomTablesExist() {
        List<String> tables =
                jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class);
        assertThat(tables).contains("bloom_versions", "bloom_artifacts");
        // 不變量 L1/L2 的 DB 層防線:scope = PUBLIC 只能綁 public tenant、
        // is_full_snapshot ⟺ base_bloom_version IS NULL。
        // 用 pg_constraint 而非 information_schema:後者只列出當前角色擁有的表,
        // 而應用是以非特權的 ctip_app 連線(ADR 0021)。
        assertThat(jdbc.queryForList(
                        "SELECT conname FROM pg_constraint c"
                                + " JOIN pg_class t ON t.oid = c.conrelid"
                                + " WHERE t.relname = 'bloom_versions' AND c.contype = 'c'",
                        String.class))
                .contains("ck_bv_base", "ck_bv_public_tenant", "ck_bv_scope", "ck_bv_fpr");
    }

    /**
     * 每一張表都必須由某支 migration 建立——不得有「schema 裡有、migration 裡沒有」的表。
     *
     * <p>原本是「未來 phase 的表不得存在」的封閉清單,那會在 Phase 14/15/18/20/21 各紅一次,
     * 而那些都是正常交付。真正該守的規則 16 語意是<strong>不得預先建立無人使用的表</strong>,
     * 這由「表必須出現在某支 migration 的 SQL 裡」表達,且不會隨 phase 推進而失效(ADR 0017)。
     */
    @Test
    void everyTableIsCreatedByAMigration() {
        List<String> tables = jdbc.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public'"
                        + " AND tablename <> 'flyway_schema_history'",
                String.class);
        String allMigrationSql = readAllMigrations();
        assertThat(tables)
                .allSatisfy(table -> assertThat(allMigrationSql)
                        .as("表 %s 不是由任何 migration 建立的", table)
                        .contains(table));
    }

    private static String readAllMigrations() {
        Path dir = classpathMigrationDir();
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (IOException e) {
                            throw new IllegalStateException("讀不到 " + path, e);
                        }
                    })
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new IllegalStateException("讀不到 migration 目錄:" + dir, e);
        }
    }

    /** Flyway 讀的位置:{@code classpath:db/migration}(application.yml)。 */
    private static Path classpathMigrationDir() {
        try {
            return Path.of(java.util.Objects.requireNonNull(
                            MigrationIntegrationTest.class.getResource("/db/migration"), "classpath 上找不到 /db/migration")
                    .toURI());
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 應用的 datasource 必須是**非特權**角色(ADR 0021)。
     *
     * <p>superuser 繞過所有 GRANT/REVOKE:以 superuser 連線時,Phase 21 的
     * {@code REVOKE UPDATE, DELETE ON audit_logs} 完全無效,而 M3-09 要求那必須由 <b>DB</b> 拒絕。
     * 實測過:`ctip` 是 postgres image 的初始 superuser(`rolsuper = t`),
     * REVOKE 之後 DELETE 照樣成功。這條斷言鎖住連線角色不會被改回 owner。
     */
    @Test
    void applicationConnectsAsANonSuperuserRole() {
        String user = jdbc.queryForObject("SELECT current_user", String.class);
        assertThat(user).as("應用不得以 owner/superuser 連線").isEqualTo("ctip_app");

        Boolean superuser =
                jdbc.queryForObject("SELECT rolsuper FROM pg_roles WHERE rolname = current_user", Boolean.class);
        assertThat(superuser).as("%s 不得是 superuser", user).isFalse();
    }

    @Test
    void requiredExtensionsInstalled() {
        List<String> extensions = jdbc.queryForList("SELECT extname FROM pg_extension", String.class);
        assertThat(extensions).contains("pgcrypto", "pg_trgm");
    }
}
