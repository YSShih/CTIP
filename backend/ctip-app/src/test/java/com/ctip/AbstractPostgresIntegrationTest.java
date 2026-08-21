package com.ctip;

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
 */
@SpringBootTest
@ActiveProfiles("mvp")
@Tag("integration")
public abstract class AbstractPostgresIntegrationTest {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void ctipEnvironment(DynamicPropertyRegistry registry) {
        registry.add("POSTGRES_HOST", POSTGRES::getHost);
        registry.add("POSTGRES_PORT", POSTGRES::getFirstMappedPort);
        registry.add("POSTGRES_DB", POSTGRES::getDatabaseName);
        registry.add("POSTGRES_USER", POSTGRES::getUsername);
        registry.add("POSTGRES_PASSWORD", POSTGRES::getPassword);
        registry.add("ENVIRONMENT", () -> "mvp");
        registry.add("JWT_SECRET", () -> "integration-test-only-secret-0123456789abcdef");
        registry.add("CORS_ALLOWED_ORIGINS", () -> "http://localhost:5173");
    }
}
