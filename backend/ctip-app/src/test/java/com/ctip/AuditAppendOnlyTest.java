package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.application.audit.AuditEvent;
import com.ctip.application.port.AuditPort;
import com.ctip.domain.audit.AuditAction;
import com.ctip.domain.audit.AuditActorType;
import com.ctip.domain.audit.AuditResult;
import com.ctip.domain.tenant.TenantId;
import com.ctip.infrastructure.audit.AuditWriter;
import com.ctip.infrastructure.retention.RetentionConnection;
import com.ctip.support.AuditProbe;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * M3-09:{@code audit_logs} 為 append-only,而且是<strong>資料庫</strong>拒絕應用角色的
 * UPDATE / DELETE(docs/spec/13-platform-ops.md §13.5 規則 1、15 §15.4)。
 *
 * <p>判準明文要求「不是被應用碼拒絕」:因此這裡直接拿應用執行期的那條連線
 * ({@code ctip_app})下 SQL,而不是呼叫任何服務。以 superuser 連線跑這個測試會永遠通過、
 * 量不到任何東西(ADR 0021),故整合測試基底自 Phase 20 起就以非特權角色連線。
 */
class AuditAppendOnlyTest extends AbstractPostgresIntegrationTest {

    /** PostgreSQL 的權限不足:{@code insufficient_privilege}。 */
    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    @Autowired
    private AuditPort audit;

    @Autowired
    private AuditWriter writer;

    @Autowired
    private DataSource applicationDataSource;

    @Autowired
    private RetentionConnection retentionConnection;

    private AuditProbe probe;

    @BeforeEach
    void setUp() {
        probe = new AuditProbe(writer, applicationDataSource);
        audit.record(AuditEvent.system(AuditAction.ADMIN_ACTION, AuditResult.SUCCESS, TenantId.PUBLIC)
                .withActor(AuditActorType.SYSTEM, null)
                .withResource("audit_logs", null));
        probe.awaitWrites();
    }

    @Test
    void theApplicationRoleCanAppendAndReadButTheDatabaseRejectsUpdate() {
        assertThat(probe.count(AuditAction.ADMIN_ACTION)).isPositive();

        assertThatThrownBy(() -> executeAsApplication("UPDATE audit_logs SET result = 'FAILURE'"))
                .isInstanceOf(SQLException.class)
                .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE));
    }

    @Test
    void theDatabaseRejectsDeleteByTheApplicationRole() {
        assertThatThrownBy(() -> executeAsApplication("DELETE FROM audit_logs"))
                .isInstanceOf(SQLException.class)
                .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE));

        assertThat(probe.count(AuditAction.ADMIN_ACTION)).isPositive();
    }

    /** §13.5 規則 6:本表永不更新,加上 {@code updated_at} 即為設計錯誤。 */
    @Test
    void theTableHasNoUpdatedAtColumn() throws SQLException {
        assertThat(columnCount("updated_at")).isZero();
        // 對照組:確認這個查詢真的量得到欄位(否則上一條斷言是空轉的)
        assertThat(columnCount("occurred_at")).isEqualTo(1);
    }

    /**
     * §13.5 規則 2:保留清理走專用角色。它刪得掉稽核列(應用角色刪不掉),
     * 但讀不到內容——欄位層級授權只給了 id 與 occurred_at(V33)。
     */
    @Test
    void theRetentionRoleCanDeleteButCannotReadTheAuditContent() {
        int deleted = retentionConnection
                .jdbc()
                .update("DELETE FROM audit_logs WHERE id IN (SELECT id FROM audit_logs LIMIT 1)");
        assertThat(deleted).isEqualTo(1);

        assertThatThrownBy(() -> retentionConnection
                        .jdbc()
                        .queryForObject("SELECT action FROM audit_logs LIMIT 1", String.class))
                .hasMessageContaining("audit_logs");
    }

    private void executeAsApplication(String sql) throws SQLException {
        try (Connection connection = applicationDataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private long columnCount(String column) throws SQLException {
        try (Connection connection = applicationDataSource.getConnection();
                Statement statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT count(*) FROM information_schema.columns"
                        + " WHERE table_name = 'audit_logs' AND column_name = '" + column + "'")) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
