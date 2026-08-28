package com.ctip.application.indicator;

import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.EventPublisherPort;
import com.ctip.application.port.IndicatorRepository;
import com.ctip.application.port.SourceRepository;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.indicator.IndicatorSourceSnapshot;
import com.ctip.domain.indicator.SourceRecordStatus;
import com.ctip.domain.shared.Visibility;
import com.ctip.domain.source.Source;
import com.ctip.sdk.SourceType;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 誤判回報(docs/spec/09-api.md §9.7 {@code POST /api/v1/iocs/{id}/report-false-positive})。
 *
 * <p>兩條規則不可讓呼叫端左右:
 * <ul>
 *   <li>作用域:只接受 {@code owner_tenant_id} = 呼叫者租戶的 Indicator;公開情資回 403</li>
 *   <li>最終狀態由 {@code IndicatorMergePolicy.determineStatus} 決定(I11 規則 2),
 *       <strong>不由呼叫端指定</strong>——還有其他 ACTIVE 來源時,狀態就不會變成 FALSE_POSITIVE</li>
 * </ul>
 */
@Service
public class FalsePositiveReportService {

    private final IndicatorRepository indicators;
    private final SourceRepository sources;
    private final EventPublisherPort events;
    private final ClockPort clock;

    public FalsePositiveReportService(
            IndicatorRepository indicators, SourceRepository sources, EventPublisherPort events, ClockPort clock) {
        this.indicators = indicators;
        this.sources = sources;
        this.events = events;
        this.clock = clock;
    }

    /**
     * @return 回報後的 Indicator;查無或跨租戶不可見時為 empty(API 層回 404)
     * @throws PublicIntelNotReportableException 目標屬於 public tenant
     */
    @Transactional
    public Optional<Indicator> report(
            IndicatorId id, String reason, String evidenceUrl, AuthenticatedIdentity reporter) {
        Optional<Indicator> found = indicators.findVisibleById(id, Visibility.authenticated(reporter.tenantId()));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Indicator indicator = found.get();
        if (!indicator.ownerTenantId().equals(reporter.tenantId())) {
            throw new PublicIntelNotReportableException(
                    "False positive reports on public intelligence go through the platform appeal process");
        }
        Source manual = sources.findBySourceType(SourceType.MANUAL)
                .orElseThrow(() -> new IllegalStateException("sources 表缺少 MANUAL 來源;V4 種子未套用?"));
        indicator.reportFalsePositive(manualReport(indicator, manual, reason, evidenceUrl), manual.reputation());
        Indicator saved = indicators.save(indicator);
        indicator.pullEvents().forEach(events::publish);
        return Optional.of(saved);
    }

    /**
     * 該來源記錄不存在時要建立的內容(§9.7「若不存在則建立」)。
     * TLP 取 Indicator 現值:新記錄會參與 {@code strictestTlp} 的重算,
     * 用 MANUAL 的預設值(AMBER)會讓一筆 CLEAR 的自家 IOC 因為被回報就變成 AMBER。
     */
    private IndicatorSourceSnapshot manualReport(
            Indicator indicator, Source manual, String reason, String evidenceUrl) {
        Instant now = clock.now();
        return new IndicatorSourceSnapshot(
                manual.id(),
                indicator.value().raw(),
                null,
                null,
                indicator.tlp(),
                now,
                now,
                null,
                manual.snapshot().redistributionPolicy(),
                1,
                SourceRecordStatus.FALSE_POSITIVE,
                Set.of(),
                payload(reason, evidenceUrl));
    }

    /** reason / evidenceUrl 落 indicator_sources.raw_payload;04 表 5 沒有為它們開欄位。 */
    private static Map<String, Object> payload(String reason, String evidenceUrl) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (reason != null && !reason.isBlank()) {
            payload.put("falsePositiveReason", reason);
        }
        if (evidenceUrl != null && !evidenceUrl.isBlank()) {
            payload.put("evidenceUrl", evidenceUrl);
        }
        return payload;
    }
}
