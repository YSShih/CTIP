package com.ctip.domain.bloom;

import com.ctip.domain.tenant.TenantId;
import java.time.Instant;

/**
 * BloomVersion 的完整狀態(建立與重建的唯一入口)。
 *
 * <p>用 snapshot 而非長參數列,是因為 checkstyle 限制方法參數 ≤ 5(01 §1.8),
 * 而本聚合有 11 個狀態欄位;record 的組件數不受該規則限制。
 */
public record BloomVersionSnapshot(
        BloomVersionId id,
        BloomScope scope,
        TenantId tenantId,
        long datasetVersion,
        long bloomVersion,
        BloomParameters parameters,
        long memberCount,
        boolean fullSnapshot,
        Long baseBloomVersion,
        Instant generatedAt,
        BloomArtifact artifact) {}
