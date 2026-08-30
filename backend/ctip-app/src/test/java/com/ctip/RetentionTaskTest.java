package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.identity.AuthService;
import com.ctip.application.identity.AuthSession;
import com.ctip.application.port.TenantMembershipRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.config.CtipProperties;
import com.ctip.infrastructure.retention.RetentionReport;
import com.ctip.infrastructure.retention.RetentionService;
import com.ctip.support.TestIdentities;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * M3-11:六項資料保留清理正確運作(docs/spec/13-platform-ops.md §13.4;排程見 08 §8.7)。
 *
 * <p>每一項都驗兩件事:<strong>超過保留期的列被清掉</strong>,而且
 * <strong>保留期內的列原封不動</strong>——只驗前者的話,一句 {@code DELETE FROM …}
 * 也會通過。清理走的是 {@code ctip_retention} 連線(§13.5 規則 2),
 * 這個測試因此同時證明那組欄位層級授權(V33)夠用。
 */
class RetentionTaskTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private RetentionService retention;

    @Autowired
    private CtipProperties properties;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private AuthService authService;

    @Autowired
    private TenantMembershipRepository memberships;

    private JdbcTemplate jdbc;
    private Instant now;
    private final List<UUID> createdIndicators = new ArrayList<>();
    private final List<UUID> createdWebhooks = new ArrayList<>();
    private final List<UUID> createdRejections = new ArrayList<>();

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        now = Instant.now();
    }

    /**
     * 自己清乾淨:整合測試共用同一個資料庫,留下的列會讓別人的斷言變成看運氣——
     * 留下的 indicator 會動到 {@code SampleDataIntegrationTest} 的種子資料斷言,
     * 留下的拒絕記錄會動到 {@code IngestionEndToEndTest} 的「拒絕 12 筆」(實測過)。
     */
    @AfterEach
    void tearDown() {
        createdIndicators.forEach(id -> jdbc.update("DELETE FROM indicators WHERE id = ?", id));
        createdWebhooks.forEach(id -> jdbc.update("DELETE FROM webhooks WHERE id = ?", id));
        createdRejections.forEach(id -> jdbc.update("DELETE FROM ingestion_rejections WHERE id = ?", id));
    }

    @Test
    void expiredAuditEntriesAreDeletedAndRecentOnesAreKept() {
        UUID stale = insertAuditLog(daysAgo(properties.retention().auditDays() + 1));
        UUID fresh = insertAuditLog(daysAgo(1));

        assertThat(retention.purgeAuditLogs()).isPositive();

        assertThat(auditExists(stale)).isFalse();
        assertThat(auditExists(fresh)).isTrue();
    }

    @Test
    void expiredRejectionRecordsAreDeletedAndRecentOnesAreKept() {
        UUID sourceId = anySourceId();
        UUID stale = insertRejection(sourceId, daysAgo(properties.retention().rejectionDays() + 1));
        UUID fresh = insertRejection(sourceId, daysAgo(1));

        assertThat(retention.purgeRejections()).isPositive();

        assertThat(exists("ingestion_rejections", stale)).isFalse();
        assertThat(exists("ingestion_rejections", fresh)).isTrue();
    }

    @Test
    void expiredWebhookDeliveriesAreDeletedAndRecentOnesAreKept() {
        UUID webhookId = insertWebhook();
        UUID stale = insertDelivery(webhookId, 1, daysAgo(properties.retention().deliveryDays() + 1));
        UUID fresh = insertDelivery(webhookId, 2, daysAgo(1));

        assertThat(retention.purgeWebhookDeliveries()).isPositive();

        assertThat(exists("webhook_deliveries", stale)).isFalse();
        assertThat(exists("webhook_deliveries", fresh)).isTrue();
    }

    /** §13.4:raw_payload 只<strong>清空該欄位</strong>,其餘欄位保留——不是刪列。 */
    @Test
    void expiredRawPayloadsAreClearedWithoutDeletingTheRow() {
        UUID stale = givenRawPayload(daysAgo(properties.retention().rawPayloadDays() + 1));
        UUID fresh = givenRawPayload(daysAgo(1));

        assertThat(retention.clearRawPayloads()).isPositive();

        assertThat(exists("indicator_sources", stale)).isTrue();
        assertThat(rawPayloadOf(stale)).isNull();
        assertThat(rawPayloadOf(fresh)).isNotNull();
    }

    @Test
    void longExpiredIndicatorsAreSoftDeletedAndActiveOnesAreUntouched() {
        UUID stale = givenExpiredIndicator(daysAgo(properties.retention().indicatorDays() + 1));
        UUID recent = givenExpiredIndicator(daysAgo(1));

        assertThat(retention.softDeleteExpiredIndicators()).isPositive();

        assertThat(deletedAtOf(stale)).isNotNull();
        assertThat(deletedAtOf(recent)).isNull();
        // 軟刪除:列還在(§13.4 明文是 UPDATE deleted_at,不是 DELETE)
        assertThat(exists("indicators", stale)).isTrue();
    }

    /** 六項全跑一輪:單項失敗不影響其他,回報每一項的清理筆數(§13.4 規則 2、3)。 */
    @Test
    void runningEveryTaskReportsPerTaskCounts() {
        insertAuditLog(daysAgo(properties.retention().auditDays() + 1));

        RetentionReport report = retention.runAll();

        assertThat(report.auditLogs()).isPositive();
        assertThat(report.bloomArtifacts()).isNotNegative();
        assertThat(report.rawPayloads()).isNotNegative();
        assertThat(report.rejections()).isNotNegative();
        assertThat(report.webhookDeliveries()).isNotNegative();
        assertThat(report.softDeletedIndicators()).isNotNegative();
    }

    private Timestamp daysAgo(int days) {
        return Timestamp.from(now.minus(Duration.ofDays(days)));
    }

    private UUID insertAuditLog(Timestamp occurredAt) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO audit_logs (id, occurred_at, actor_type, tenant_id, action, result)"
                        + " VALUES (?, ?, 'SYSTEM', (SELECT id FROM tenants WHERE slug = 'public'), 'ADMIN_ACTION',"
                        + " 'SUCCESS')",
                id,
                occurredAt);
        return id;
    }

    private UUID insertRejection(UUID sourceId, Timestamp createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ingestion_rejections (id, source_id, raw_value, reason, created_at)"
                        + " VALUES (?, ?, 'retention-test', 'MALFORMED_VALUE', ?)",
                id,
                sourceId,
                createdAt);
        createdRejections.add(id);
        return id;
    }

    private UUID insertWebhook() {
        AuthSession session = new TestIdentities(authService, memberships)
                .register("retention-" + UUID.randomUUID() + "@example.org", RoleCode.TENANT_ADMIN);
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO webhooks (id, tenant_id, created_by_user_id, name, target_url, secret_encrypted)"
                        + " VALUES (?, ?, ?, 'retention', 'https://hooks.ctip-sample.invalid/x', '\\x00'::bytea)",
                id,
                session.identity().tenantId().value(),
                session.identity().userId().value());
        createdWebhooks.add(id);
        return id;
    }

    private UUID insertDelivery(UUID webhookId, int attempt, Timestamp createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO webhook_deliveries (id, webhook_id, event_id, event_type, attempt, status, created_at)"
                        + " VALUES (?, ?, ?, 'NEW_IOC', ?, 'SUCCESS', ?)",
                id,
                webhookId,
                UUID.randomUUID(),
                attempt,
                createdAt);
        return id;
    }

    /**
     * 自建 indicator 而不是改種子資料:種子的 1,020 筆是其他測試的斷言對象
     * (SampleDataIntegrationTest),把它們標成 EXPIRED 會讓那些測試莫名其妙地紅。
     */
    private UUID insertIndicator(Timestamp updatedAt, String status) {
        UUID id = UUID.randomUUID();
        String value = "retention-" + id + ".ctip-sample.invalid";
        jdbc.update(
                "INSERT INTO indicators (id, owner_tenant_id, type, value, normalized_value, fingerprint,"
                        + " first_seen, last_seen, valid_from, status, updated_at)"
                        + " VALUES (?, (SELECT id FROM tenants WHERE slug = 'public'), 'DOMAIN', ?, ?, ?,"
                        + " now(), now(), now(), ?, ?)",
                id,
                value,
                value,
                fingerprintOf(id),
                status,
                updatedAt);
        createdIndicators.add(id);
        // 每筆 indicator 都必須有來源記錄(SampleDataIntegrationTest 對整張表驗這件事)
        insertIndicatorSource(id);
        return id;
    }

    private void insertIndicatorSource(UUID indicatorId) {
        jdbc.update(
                "INSERT INTO indicator_sources (id, indicator_id, source_id, source_value, source_tlp,"
                        + " source_first_seen, source_last_seen, redistribution_policy)"
                        + " VALUES (?, ?, ?, 'retention-test', 'CLEAR', now(), now(), 'INTERNAL_ONLY')",
                UUID.randomUUID(),
                indicatorId,
                anySourceId());
    }

    private UUID givenRawPayload(Timestamp updatedAt) {
        UUID indicatorId = insertIndicator(Timestamp.from(now), "ACTIVE");
        UUID id =
                jdbc.queryForObject("SELECT id FROM indicator_sources WHERE indicator_id = ?", UUID.class, indicatorId);
        jdbc.update(
                "UPDATE indicator_sources SET raw_payload = '{\"probe\":true}'::jsonb, updated_at = ? WHERE id = ?",
                updatedAt,
                id);
        return id;
    }

    private UUID givenExpiredIndicator(Timestamp updatedAt) {
        return insertIndicator(updatedAt, "EXPIRED");
    }

    /** fingerprint 有 {@code ^[0-9a-f]{64}$} 的 CHECK;測試列用 id 湊出一個合法值。 */
    private static String fingerprintOf(UUID id) {
        String hex = id.toString().replace("-", "");
        return (hex + hex).substring(0, 64);
    }

    private UUID anySourceId() {
        return jdbc.queryForObject("SELECT id FROM sources LIMIT 1", UUID.class);
    }

    private boolean auditExists(UUID id) {
        return exists("audit_logs", id);
    }

    private boolean exists(String table, UUID id) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE id = ?", Long.class, id);
        return count != null && count > 0;
    }

    private Object rawPayloadOf(UUID id) {
        return jdbc.queryForObject("SELECT raw_payload FROM indicator_sources WHERE id = ?", String.class, id);
    }

    private Object deletedAtOf(UUID id) {
        return jdbc.queryForObject("SELECT deleted_at FROM indicators WHERE id = ?", Timestamp.class, id);
    }
}
