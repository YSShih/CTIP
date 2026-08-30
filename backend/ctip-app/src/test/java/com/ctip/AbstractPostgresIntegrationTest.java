package com.ctip;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * L3 整合測試基底:單例 PostgreSQL Testcontainer + mvp profile。
 * 透過真實的 application.yml 環境變數對應(docs/spec/05-environment.md §5.7)注入連線,
 * 讓測試同時驗證 §5.7 的對應契約。
 *
 * <p>連線分兩個角色,與 compose 一致(ADR 0021):Flyway 用容器的 owner(superuser)跑 DDL,
 * 應用執行期用非特權的 {@code ctip_app}。<strong>測試必須跟正式環境用同一組權限</strong>,
 * 否則 Phase 21 的 {@code AuditAppendOnlyTest}(M3-09:UPDATE/DELETE 必須被 DB 拒絕)
 * 會在 superuser 連線下永遠通過,量不到任何東西。
 */
@SpringBootTest
@ActiveProfiles("mvp")
@Tag("integration")
public abstract class AbstractPostgresIntegrationTest {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    /** 子類別若要另外啟一個 app 實例(DistributedRateLimitTest),必須用同一組非特權連線。 */
    protected static final String APP_USER = "ctip_app";

    protected static final String APP_PASSWORD = "ctip_app_test";

    /** 保留清理專用角色(13 §13.5 規則 2);V33 逐表授權,這裡只負責把角色建出來。 */
    protected static final String RETENTION_USER = "ctip_retention";

    protected static final String RETENTION_PASSWORD = "ctip_retention_test";

    /** 與 {@link #ctipEnvironment} 一致;第二個實例得用同一把金鑰才能驗同一組 token。 */
    protected static final String TEST_JWT_SECRET = "integration-test-only-secret-0123456789abcdef";

    /** Bloom artifact 的測試根目錄;整個 JVM 共用,才不會讓每個測試類各起一個 Spring context。 */
    protected static final Path BLOOM_DIR = createBloomDirectory();

    static {
        POSTGRES.start();
        createApplicationRoles();
    }

    /** 對應 compose 的 {@code config/postgres/01-app-roles.sh};兩者內容必須保持一致。 */
    private static void createApplicationRoles() {
        String owner = POSTGRES.getUsername();
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), owner, POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute(createRole(APP_USER, APP_PASSWORD));
            statement.execute(createRole(RETENTION_USER, RETENTION_PASSWORD));
            statement.execute("GRANT USAGE ON SCHEMA public TO " + RETENTION_USER);
            statement.execute("GRANT USAGE ON SCHEMA public TO " + APP_USER);
            statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO " + APP_USER);
            statement.execute("GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO " + APP_USER);
            statement.execute("ALTER DEFAULT PRIVILEGES FOR ROLE " + owner
                    + " IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO " + APP_USER);
            statement.execute("ALTER DEFAULT PRIVILEGES FOR ROLE " + owner
                    + " IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO " + APP_USER);
        } catch (SQLException e) {
            throw new IllegalStateException("建立測試用的非特權角色失敗", e);
        }
    }

    private static String createRole(String role, String password) {
        return "DO $$ BEGIN"
                + " IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '" + role + "') THEN"
                + " CREATE ROLE " + role + " LOGIN PASSWORD '" + password + "'"
                + " NOSUPERUSER NOCREATEDB NOCREATEROLE;"
                + " END IF; END $$;";
    }

    private static Path createBloomDirectory() {
        try {
            return Files.createTempDirectory("ctip-bloom-test");
        } catch (IOException e) {
            throw new UncheckedIOException("無法建立 Bloom 測試目錄", e);
        }
    }

    /**
     * 每個 Spring context 的連線池上限。
     *
     * <p>Spring 的 test context 是<strong>快取的</strong>——整批測試跑完之前,每一個不同組態的
     * context 都還活著,而每個都有自己的 HikariCP 池。預設的 10 條 × 二十幾個 context
     * 會撞上 PostgreSQL 的 {@code max_connections}(預設 100),症狀是
     * {@code FATAL: remaining connection slots are reserved} ——它出現在<strong>後面</strong>
     * 才載入的那個 context 上,看起來完全像是那個測試自己的問題(Phase 20 實測)。
     * 整合測試是單執行緒的,4 條綽綽有餘。
     */
    private static final int TEST_POOL_SIZE = 4;

    @DynamicPropertySource
    static void ctipEnvironment(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> TEST_POOL_SIZE);
        registry.add("POSTGRES_HOST", POSTGRES::getHost);
        registry.add("POSTGRES_PORT", POSTGRES::getFirstMappedPort);
        registry.add("POSTGRES_DB", POSTGRES::getDatabaseName);
        registry.add("POSTGRES_USER", POSTGRES::getUsername);
        registry.add("POSTGRES_PASSWORD", POSTGRES::getPassword);
        registry.add("POSTGRES_APP_USER", () -> APP_USER);
        registry.add("POSTGRES_APP_PASSWORD", () -> APP_PASSWORD);
        registry.add("POSTGRES_RETENTION_USER", () -> RETENTION_USER);
        registry.add("POSTGRES_RETENTION_PASSWORD", () -> RETENTION_PASSWORD);
        // 取樣是機率,測試不能靠機率:整合測試一律全記(§13.5 規則 4 的比率本身由單元測試驗)
        registry.add("AUDIT_SAMPLE_READ_RATE", () -> "1.0");
        registry.add("ENVIRONMENT", () -> "mvp");
        registry.add("JWT_SECRET", () -> TEST_JWT_SECRET);
        // webhook 簽章密鑰的 KEK(不變量 W2 定調,ADR 0021);測試用固定值,與 prod 無關
        registry.add("WEBHOOK_SECRET_KEK", () -> "integration-test-only-webhook-kek-0123456789");
        registry.add("CORS_ALLOWED_ORIGINS", () -> "http://localhost:5173");
        // 排程在測試中一律關閉,避免 @Scheduled 任務與測試資料互相干擾(docs/spec/08 §8.7 總開關)
        registry.add("SCHEDULER_ENABLED", () -> "false");
        // mvp 環境的限流後端(避免對 redis 預設值發出 WARN 誤導)
        registry.add("RATE_LIMIT_BACKEND", () -> "memory");
        // Bloom artifact 寫到臨時目錄:預設的 /var/lib/ctip/bloom 只存在於容器內。
        // 容量縮小到 10 萬(預設 1000 萬 → 每份 18MB 的位元陣列),測試才不會為此配置整份記憶體;
        // 位元格式本身由 BloomBitLayoutTest 以固定參數驗證,與此處的容量無關。
        registry.add("BLOOM_STORAGE_DIR", () -> BLOOM_DIR.toString());
        registry.add("BLOOM_PUBLIC_CAPACITY", () -> "100000");
        registry.add("BLOOM_TENANT_DEFAULT_CAPACITY", () -> "10000");
    }
}
