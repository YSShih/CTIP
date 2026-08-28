package com.ctip.domain.bloom;

import com.ctip.domain.tenant.TenantId;

/**
 * 一份 artifact 的邏輯位址。實體路徑(目錄配置、副檔名)由儲存實作決定,
 * 這裡只表達「哪個 scope 的哪個租戶、哪個 dataset 的哪個版本、是 full 還是 delta」。
 */
public record BloomArtifactLocation(
        BloomScope scope, TenantId tenantId, long datasetVersion, long bloomVersion, boolean fullSnapshot) {}
