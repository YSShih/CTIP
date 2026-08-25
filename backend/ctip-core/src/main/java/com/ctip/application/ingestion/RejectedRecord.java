package com.ctip.application.ingestion;

import com.ctip.domain.source.SourceId;
import com.ctip.sdk.IocType;
import java.util.UUID;

/** 寫入 ingestion_rejections 的一筆拒絕(§7.3:不得靜默接受、不得靜默丟棄)。 */
public record RejectedRecord(
        SourceId sourceId,
        UUID sourceSyncId,
        String rawValue,
        IocType declaredType,
        RejectionReason reason,
        String detail) {}
