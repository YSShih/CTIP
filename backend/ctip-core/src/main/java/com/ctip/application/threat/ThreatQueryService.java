package com.ctip.application.threat;

import com.ctip.application.port.IndicatorRepository;
import com.ctip.application.port.ThreatRepository;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.shared.Cursor;
import com.ctip.domain.shared.CursorPage;
import com.ctip.domain.shared.Visibility;
import com.ctip.domain.threat.Threat;
import com.ctip.domain.threat.ThreatId;
import com.ctip.domain.threat.ThreatIndicatorLink;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Threat 讀取端點的查詢編排(docs/spec/09-api.md §9.1「Threat」,三個端點皆匿名可用)。
 *
 * <p>可見度分兩段,兩段都不能省:
 * <ol>
 *   <li>Threat 自身:{@code owner_tenant_id IN (viewer, public)} + public 分支的 TLP 上限
 *       (§7.7),由 repository 的 Specification 強制</li>
 *   <li>關聯的 Indicator:再走一次 Indicator 的可見度(含再散布規則 3)——
 *       關聯不是可見度的旁路</li>
 * </ol>
 */
@Service
public class ThreatQueryService {

    private final ThreatRepository threats;
    private final IndicatorRepository indicators;

    public ThreatQueryService(ThreatRepository threats, IndicatorRepository indicators) {
        this.threats = threats;
        this.indicators = indicators;
    }

    public CursorPage<Threat> list(ThreatFilter filter, Visibility visibility, Cursor after, int limit) {
        return threats.findVisible(visibility, filter, after, limit);
    }

    public Optional<Threat> byId(ThreatId id, Visibility visibility) {
        return threats.findVisibleById(id, visibility);
    }

    /** 關聯的 IOC(依 addedAt 遞增);viewer 看不到的 Indicator 直接不出現在結果中。 */
    public List<LinkedIndicator> linkedIndicators(Threat threat, Visibility visibility) {
        List<ThreatIndicatorLink> links = threat.indicators();
        if (links.isEmpty()) {
            return List.of();
        }
        Map<IndicatorId, Indicator> visible =
                indicators
                        .findVisibleByIds(
                                links.stream()
                                        .map(ThreatIndicatorLink::indicatorId)
                                        .toList(),
                                visibility)
                        .stream()
                        .collect(Collectors.toMap(Indicator::id, Function.identity()));
        return links.stream()
                .sorted(java.util.Comparator.comparing(ThreatIndicatorLink::addedAt))
                .filter(link -> visible.containsKey(link.indicatorId()))
                .map(link -> new LinkedIndicator(visible.get(link.indicatorId()), link.role(), link.addedAt()))
                .toList();
    }
}
