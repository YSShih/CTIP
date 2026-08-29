package com.ctip.support;

import com.ctip.application.port.EventPublisherPort;
import com.ctip.application.port.IndicatorRepository;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.indicator.IndicatorSnapshot;
import com.ctip.domain.indicator.IndicatorSource;
import com.ctip.domain.indicator.IndicatorSourceSnapshot;
import com.ctip.domain.source.Reputation;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.Tlp;
import java.util.UUID;

/**
 * 把手動提交的 IOC 改寫成「公開情資」的測試工具:owner = public tenant、TLP = CLEAR,
 * 再散布政策可指定——等同 feed 攝取的結果,而 {@code ioc:publish} 的端點路徑本身另有測試覆蓋。
 *
 * <p>另提供「事後收緊 TLP」:{@code IndicatorTlpTightened} 是 Threat 的 H6 一致性規則的觸發點
 * (ADR 0020),被測對象是事件的消費端。
 */
public final class PublishedIndicators {

    private final IndicatorRepository indicators;
    private final EventPublisherPort events;

    public PublishedIndicators(IndicatorRepository indicators, EventPublisherPort events) {
        this.indicators = indicators;
        this.events = events;
    }

    /** 改寫為 public tenant 的 CLEAR 情資,來源記錄套用指定的再散布政策(§7.9 規則 1:條款會變)。 */
    public void publish(String iocId, RedistributionPolicy policy) {
        indicators.save(Indicator.reconstitute(publicClearSnapshot(byId(iocId), policy)));
    }

    /** 讓既有的來源記錄變 AMBER 並重新合併 → 發佈 IndicatorTlpTightened。 */
    public void tightenToAmber(String iocId) {
        Indicator indicator = byId(iocId);
        IndicatorSourceSnapshot record = indicator.snapshot().sources().getFirst();
        indicator.mergeFrom(
                new IndicatorSource(withTlp(record, Tlp.AMBER, record.redistributionPolicy())), new Reputation(70));
        indicators.save(indicator);
        // 此處無交易,SpringEventPublisherAdapter 會立即發佈(端點路徑上則是 AFTER_COMMIT)
        indicator.pullEvents().forEach(events::publish);
    }

    public Indicator byId(String iocId) {
        return indicators.findById(new IndicatorId(UUID.fromString(iocId))).orElseThrow();
    }

    private static IndicatorSnapshot publicClearSnapshot(Indicator submitted, RedistributionPolicy policy) {
        IndicatorSnapshot s = submitted.snapshot();
        return new IndicatorSnapshot(
                s.id(),
                TenantId.PUBLIC,
                s.value(),
                s.fingerprint(),
                s.firstSeen(),
                s.lastSeen(),
                s.validUntil(),
                s.confidence(),
                s.severity(),
                s.score(),
                Tlp.CLEAR,
                s.status(),
                s.tags(),
                s.sources().stream().map(r -> withTlp(r, Tlp.CLEAR, policy)).toList(),
                s.hashRecords());
    }

    private static IndicatorSourceSnapshot withTlp(
            IndicatorSourceSnapshot record, Tlp tlp, RedistributionPolicy policy) {
        return new IndicatorSourceSnapshot(
                record.sourceId(),
                record.sourceValue(),
                record.sourceConfidence(),
                record.sourceSeverity(),
                tlp,
                record.sourceFirstSeen(),
                record.sourceLastSeen(),
                record.sourceValidUntil(),
                policy,
                record.reportCount(),
                record.status(),
                record.tags(),
                record.rawPayload());
    }
}
