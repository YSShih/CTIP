package com.ctip.application.ingestion;

import com.ctip.domain.source.SourceId;
import com.ctip.sdk.IocType;
import java.util.UUID;

/**
 * 寫入 ingestion_rejections 的一筆拒絕(§7.3:不得靜默接受、不得靜默丟棄)。
 * sourceSyncId 與 importJobId 至多其一非 null(04 表 7、18b)。
 */
public record RejectedRecord(
        SourceId sourceId,
        UUID sourceSyncId,
        UUID importJobId,
        String rawValue,
        IocType declaredType,
        RejectionReason reason,
        String detail) {}
