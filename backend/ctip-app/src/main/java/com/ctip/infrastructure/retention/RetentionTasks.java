package com.ctip.infrastructure.retention;

import com.ctip.application.port.ClockPort;
import java.sql.Timestamp;
import java.time.Duration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 五項以 SQL 表達的保留清理(docs/spec/13-platform-ops.md §13.4)。
 *
 * <p>三條強制規則,全部落在這個類別上:
 * <ol>
 *   <li><strong>分批</strong>:每批上限 {@value #BATCH_SIZE} 列,避免長交易鎖表。
 *       單一大交易在 180 天份的稽核軌跡上會鎖住整張表。</li>
 *   <li><strong>專用角色</strong>:此處的 {@link JdbcTemplate} 綁在 {@code ctip_retention} 的
 *       連線上(§13.5 規則 2)。應用角色對 {@code audit_logs} 連 DELETE 都沒有。</li>
 *   <li>回傳清理筆數,由 {@link RetentionService} 記錄。</li>
 * </ol>
 *
 * <p>批次以 {@code id IN (SELECT … LIMIT n)} 表達而不是 {@code ctid}:清理角色只有
 * <strong>欄位層級</strong>的 SELECT(V33),而系統欄位不在授權範圍內。
 */
public class RetentionTasks {

    static final int BATCH_SIZE = 10_000;

    /** 單次執行的批次上限(= 1 億列)。防的是條件寫錯時無限迴圈,不是正常量。 */
    private static final int MAX_BATCHES = 10_000;

    private static final String DELETE_AUDIT_LOGS = """
            DELETE FROM audit_logs WHERE id IN (
                SELECT id FROM audit_logs WHERE occurred_at < ? ORDER BY occurred_at LIMIT %d)
            """.formatted(BATCH_SIZE);

    private static final String DELETE_REJECTIONS = """
            DELETE FROM ingestion_rejections WHERE id IN (
                SELECT id FROM ingestion_rejections WHERE created_at < ? ORDER BY created_at LIMIT %d)
            """.formatted(BATCH_SIZE);

    private static final String DELETE_DELIVERIES = """
            DELETE FROM webhook_deliveries WHERE id IN (
                SELECT id FROM webhook_deliveries WHERE created_at < ? ORDER BY created_at LIMIT %d)
            """.formatted(BATCH_SIZE);

    /** 只清空欄位,保留其餘欄位(§13.4)——這一列的觀測事實仍然有效,只是不再留著原始 payload。 */
    private static final String CLEAR_RAW_PAYLOADS = """
            UPDATE indicator_sources SET raw_payload = NULL WHERE id IN (
                SELECT id FROM indicator_sources
                WHERE updated_at < ? AND raw_payload IS NOT NULL
                ORDER BY updated_at LIMIT %d)
            """.formatted(BATCH_SIZE);

    /** EXPIRED 的 indicator 於保留期後<strong>軟</strong>刪除(§13.4);列本身留著。 */
    private static final String SOFT_DELETE_INDICATORS = """
            UPDATE indicators SET deleted_at = ? WHERE id IN (
                SELECT id FROM indicators
                WHERE status = 'EXPIRED' AND deleted_at IS NULL AND updated_at < ?
                ORDER BY updated_at LIMIT %d)
            """.formatted(BATCH_SIZE);

    private final JdbcTemplate jdbc;
    private final ClockPort clock;
    private final RetentionPolicy policy;

    public RetentionTasks(JdbcTemplate jdbc, ClockPort clock, RetentionPolicy policy) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.policy = policy;
    }

    public int purgeAuditLogs() {
        return inBatches(cutoff -> jdbc.update(DELETE_AUDIT_LOGS, cutoff), policy.auditDays());
    }

    public int purgeRejections() {
        return inBatches(cutoff -> jdbc.update(DELETE_REJECTIONS, cutoff), policy.rejectionDays());
    }

    public int purgeWebhookDeliveries() {
        return inBatches(cutoff -> jdbc.update(DELETE_DELIVERIES, cutoff), policy.deliveryDays());
    }

    public int clearRawPayloads() {
        return inBatches(cutoff -> jdbc.update(CLEAR_RAW_PAYLOADS, cutoff), policy.rawPayloadDays());
    }

    public int softDeleteExpiredIndicators() {
        Timestamp now = Timestamp.from(clock.now());
        return inBatches(cutoff -> jdbc.update(SOFT_DELETE_INDICATORS, now, cutoff), policy.indicatorDays());
    }

    private int inBatches(Batch batch, int retentionDays) {
        Timestamp cutoff = Timestamp.from(clock.now().minus(Duration.ofDays(retentionDays)));
        int total = 0;
        for (int i = 0; i < MAX_BATCHES; i++) {
            int affected = batch.run(cutoff);
            total += affected;
            if (affected < BATCH_SIZE) {
                return total;
            }
        }
        return total;
    }

    /** 每批一個獨立交易({@link JdbcTemplate} 預設 autocommit),不是一個大交易。 */
    @FunctionalInterface
    private interface Batch {
        int run(Timestamp cutoff);
    }
}
