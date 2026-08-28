package com.ctip;

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

    private static final String APP_USER = "ctip_app";
    private static final String APP_PASSWORD = "ctip_app_test";

    static {
        POSTGRES.start();
        createApplicationRole();
    }

    /** 對應 compose 的 {@code config/postgres/01-app-roles.sh};兩者內容必須保持一致。 */
    private static void createApplicationRole() {
        String owner = POSTGRES.getUsername();
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), owner, POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("DO $$ BEGIN"
                    + " IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '" + APP_USER + "') THEN"
                    + " CREATE ROLE " + APP_USER + " LOGIN PASSWORD '" + APP_PASSWORD + "'"
                    + " NOSUPERUSER NOCREATEDB NOCREATEROLE;"
                    + " END IF; END $$;");
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

    @DynamicPropertySource
    static void ctipEnvironment(DynamicPropertyRegistry registry) {
        registry.add("POSTGRES_HOST", POSTGRES::getHost);
        registry.add("POSTGRES_PORT", POSTGRES::getFirstMappedPort);
        registry.add("POSTGRES_DB", POSTGRES::getDatabaseName);
        registry.add("POSTGRES_USER", POSTGRES::getUsername);
        registry.add("POSTGRES_PASSWORD", POSTGRES::getPassword);
        registry.add("POSTGRES_APP_USER", () -> APP_USER);
        registry.add("POSTGRES_APP_PASSWORD", () -> APP_PASSWORD);
        registry.add("ENVIRONMENT", () -> "mvp");
        registry.add("JWT_SECRET", () -> "integration-test-only-secret-0123456789abcdef");
        registry.add("CORS_ALLOWED_ORIGINS", () -> "http://localhost:5173");
        // 排程在測試中一律關閉,避免 @Scheduled 任務與測試資料互相干擾(docs/spec/08 §8.7 總開關)
        registry.add("SCHEDULER_ENABLED", () -> "false");
        // mvp 環境的限流後端(避免對 redis 預設值發出 WARN 誤導)
        registry.add("RATE_LIMIT_BACKEND", () -> "memory");
    }
}
