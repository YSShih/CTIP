package com.ctip.infrastructure.event;

import com.ctip.application.stix.ThreatStixProjectionService;
import com.ctip.application.threat.ThreatService;
import com.ctip.domain.event.IndicatorEvents.IndicatorTlpTightened;
import com.ctip.domain.event.ThreatEvents.ThreatUpdated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Domain event 的第一個消費端(docs/spec/02-ddd-model.md §2.4)。事件由
 * {@link SpringEventPublisherAdapter} 於 <strong>AFTER_COMMIT</strong> 發佈,因此這裡收到時
 * 交易已提交——用 {@code @EventListener} 即可,不需要再宣告一次 transactional phase。
 *
 * <p>兩件事:
 * <ul>
 *   <li>{@code IndicatorTlpTightened} → 收緊關聯 Threat 的 TLP(H6 的事後維持,ADR 0020)</li>
 *   <li>{@code ThreatUpdated} → 重投影 STIX({@code malware}/{@code attack-pattern} +
 *       {@code relationship});投影是衍生資料,失敗不得影響已提交的變更(§7.8.6)</li>
 * </ul>
 *
 * <p>不會無限遞迴:收緊會再發一次 {@code ThreatUpdated},而那條分支只做投影。
 */
@Component
class ThreatConsistencyListener {

    private static final Logger log = LoggerFactory.getLogger(ThreatConsistencyListener.class);

    private final ThreatService threats;
    private final ThreatStixProjectionService projections;

    ThreatConsistencyListener(ThreatService threats, ThreatStixProjectionService projections) {
        this.threats = threats;
        this.projections = projections;
    }

    @EventListener
    void onIndicatorTlpTightened(DomainEventEnvelope envelope) {
        if (!(envelope.event() instanceof IndicatorTlpTightened event)) {
            return;
        }
        try {
            int tightened = threats.retightenForIndicator(event.indicatorId(), event.currentTlp());
            if (tightened > 0) {
                log.info(
                        "Indicator {} 的 TLP 收緊為 {},連帶收緊 {} 個關聯 Threat(H6)",
                        event.indicatorId().value(),
                        event.currentTlp(),
                        tightened);
            }
        } catch (RuntimeException e) {
            // 一致性維護失敗不得回頭影響已提交的 ingestion;記錄下來,下一次合併或關聯變更會再試
            log.warn("H6 的 Threat TLP 重新收緊失敗:indicator={}", event.indicatorId().value(), e);
        }
    }

    @EventListener
    void onThreatUpdated(DomainEventEnvelope envelope) {
        if (envelope.event() instanceof ThreatUpdated event) {
            projections.project(event.threatId());
        }
    }
}
