package com.ctip.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.domain.audit.AuditAction;
import com.ctip.infrastructure.audit.AuditWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.sql.DataSource;

/**
 * 稽核測試的共用探針:等待非同步寫入排空,並直接讀 {@code audit_logs}。
 *
 * <p>讀取走 JDBC 而不是 {@code GET /audit-logs}:那支端點只回呼叫者自己的租戶,
 * 而完整性測試要看的是「26 種行為在整個平台上是否都寫得出來」。
 */
public final class AuditProbe {

    /** 寫入是非同步的;等它排空,斷言才不必靠時間。 */
    private static final long QUIESCENCE_MILLIS = 5_000;

    private final AuditWriter writer;
    private final DataSource dataSource;

    public AuditProbe(AuditWriter writer, DataSource dataSource) {
        this.writer = writer;
        this.dataSource = dataSource;
    }

    public void awaitWrites() {
        writer.awaitQuiescence(QUIESCENCE_MILLIS);
    }

    public Set<AuditAction> recordedActions() {
        awaitWrites();
        Set<AuditAction> actions = new LinkedHashSet<>();
        query("SELECT DISTINCT action FROM audit_logs", rows -> {
            while (rows.next()) {
                actions.add(AuditAction.valueOf(rows.getString(1)));
            }
        });
        return actions;
    }

    public long count(AuditAction action) {
        awaitWrites();
        long[] count = {0};
        query("SELECT count(*) FROM audit_logs WHERE action = '" + action.name() + "'", rows -> {
            rows.next();
            count[0] = rows.getLong(1);
        });
        return count[0];
    }

    /** 最近一列的某個欄位;供斷言 actor / ip / trace_id 等環境欄位確實被補齊。 */
    public String latestColumn(AuditAction action, String column) {
        awaitWrites();
        String[] value = {null};
        query(
                "SELECT " + column + " FROM audit_logs WHERE action = '" + action.name()
                        + "' ORDER BY occurred_at DESC LIMIT 1",
                rows -> {
                    assertThat(rows.next()).as("沒有任何 %s 稽核列", action).isTrue();
                    value[0] = rows.getString(1);
                });
        return value[0];
    }

    private void query(String sql, ResultConsumer consumer) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rows = statement.executeQuery()) {
            consumer.accept(rows);
        } catch (SQLException e) {
            throw new IllegalStateException("讀取 audit_logs 失敗:" + sql, e);
        }
    }

    @FunctionalInterface
    private interface ResultConsumer {
        void accept(ResultSet rows) throws SQLException;
    }
}
