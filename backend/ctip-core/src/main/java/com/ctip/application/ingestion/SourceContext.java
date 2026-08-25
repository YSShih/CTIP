package com.ctip.application.ingestion;

import com.ctip.domain.source.Reputation;
import com.ctip.domain.source.Source;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.source.SourceSnapshot;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.Tlp;

/**
 * 一次 ingestion 中來源的固定屬性快照:defaultTlp 與 redistributionPolicy 以 DB(sources 表)
 * 為準快照進 indicator_sources(§7.9 規則 1);allowsPrivateIps 是 §7.3 「來源明示允許」
 * 的擴充點,M1 恆為 false。
 */
public record SourceContext(
        SourceId sourceId,
        TenantId ownerTenantId,
        Tlp defaultTlp,
        RedistributionPolicy redistributionPolicy,
        Reputation reputation,
        boolean allowsPrivateIps) {

    /** M1 feed ingestion:資料落 public tenant(手動提交的租戶歸屬是 Phase 14)。 */
    public static SourceContext publicFeed(Source source) {
        SourceSnapshot s = source.snapshot();
        return new SourceContext(
                s.id(), TenantId.PUBLIC, s.defaultTlp(), s.redistributionPolicy(), s.reputation(), false);
    }
}
