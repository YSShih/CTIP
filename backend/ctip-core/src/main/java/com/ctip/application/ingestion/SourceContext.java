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

    /** feed ingestion:資料落 public tenant。 */
    public static SourceContext publicFeed(Source source) {
        SourceSnapshot s = source.snapshot();
        return new SourceContext(
                s.id(), TenantId.PUBLIC, s.defaultTlp(), s.redistributionPolicy(), s.reputation(), false);
    }

    /**
     * 手動提交／匯入(Phase 14):owner 是提交者的租戶。
     *
     * <p>TLP 與再散布政策都是<strong>此次提交</strong>的值,不取來源預設——同一個 MANUAL 來源
     * 承載所有租戶的提交,用來源預設會讓每一筆提交都變成 {@code AMBER} / {@code INTERNAL_ONLY},
     * 提交者指定的 TLP 完全失效,且公開發布會產生一筆誰都看不到的資料(ADR 0023)。
     * 允許的值由呼叫端驗證(§9.7:預設 AMBER + INTERNAL_ONLY;CLEAR/GREEN 需 {@code ioc:publish})。
     */
    public static SourceContext manualSubmission(
            Source source, TenantId ownerTenantId, Tlp tlp, RedistributionPolicy redistributionPolicy) {
        SourceSnapshot s = source.snapshot();
        return new SourceContext(s.id(), ownerTenantId, tlp, redistributionPolicy, s.reputation(), false);
    }
}
