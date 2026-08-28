package com.ctip.application.ingestion;

import com.ctip.sdk.IocHashType;
import com.ctip.sdk.IocType;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import java.time.Instant;
import java.util.Set;

/**
 * 單筆手動提交(docs/spec/09-api.md §9.7 {@code POST /api/v1/iocs} 的請求內容)。
 *
 * <p>刻意<strong>沒有</strong> {@code ownerTenantId}:歸屬由提交者的身分決定,不可指定(§9.7)。
 * {@code tlp} 為 null 時取預設 {@code AMBER}。
 */
public record ManualSubmissionCommand(
        IocType type,
        String value,
        IocHashType hashType,
        Integer confidence,
        Severity severity,
        Tlp tlp,
        Instant validUntil,
        Set<String> tags,
        String note) {}
