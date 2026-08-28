package com.ctip.application.ingestion;

import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

/**
 * 一次檔案匯入的狀態(docs/spec/04-data-dictionary.md 表 18b;09 §9.7 的 202 + jobId 進度查詢)。
 *
 * <p>兩模型表,不是聚合根(§2.2 的九個聚合不含它):狀態轉換是純函數,
 * 每次轉換回傳新實例,交易邊界由 {@code ImportService} 持有。
 */
public record ImportJob(
        ImportJobId id,
        TenantId tenantId,
        UserId submittedBy,
        ImportJobStatus status,
        ImportFormat format,
        Integer totalRows,
        int acceptedCount,
        int mergedCount,
        int rejectedCount,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt) {

    /** error_message 欄位上限(表 18b)。 */
    private static final int MAX_ERROR_LENGTH = 1024;

    public ImportJob {
        Objects.requireNonNull(id, "id 不得為 null");
        Objects.requireNonNull(tenantId, "tenantId 不得為 null");
        Objects.requireNonNull(submittedBy, "submittedBy 不得為 null");
        Objects.requireNonNull(status, "status 不得為 null");
        Objects.requireNonNull(format, "format 不得為 null");
        if (acceptedCount < 0 || mergedCount < 0 || rejectedCount < 0) {
            throw new IllegalArgumentException("匯入計數不得為負");
        }
    }

    public ImportJob running(Instant now) {
        return new ImportJob(
                id,
                tenantId,
                submittedBy,
                ImportJobStatus.RUNNING,
                format,
                totalRows,
                acceptedCount,
                mergedCount,
                rejectedCount,
                null,
                now,
                null,
                createdAt);
    }

    /** 終態:有拒絕筆數即 PARTIAL,全數接受為 SUCCESS。 */
    public ImportJob finished(BatchOutcome outcome, Instant now) {
        ImportJobStatus finalStatus = outcome.rejected() > 0 ? ImportJobStatus.PARTIAL : ImportJobStatus.SUCCESS;
        return new ImportJob(
                id,
                tenantId,
                submittedBy,
                finalStatus,
                format,
                totalRows,
                outcome.accepted() - outcome.merged(),
                outcome.merged(),
                outcome.rejected(),
                null,
                startedAt,
                now,
                createdAt);
    }

    /** 整批失敗(解析錯誤等);訊息截到欄位上限。 */
    public ImportJob failed(String reason, Instant now) {
        return new ImportJob(
                id,
                tenantId,
                submittedBy,
                ImportJobStatus.FAILURE,
                format,
                totalRows,
                acceptedCount,
                mergedCount,
                rejectedCount,
                truncate(reason),
                startedAt,
                now,
                createdAt);
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= MAX_ERROR_LENGTH ? reason : reason.substring(0, MAX_ERROR_LENGTH);
    }
}
