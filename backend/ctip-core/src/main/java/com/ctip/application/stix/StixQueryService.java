package com.ctip.application.stix;

import com.ctip.application.indicator.RedistributionFilter;
import com.ctip.application.port.IndicatorRepository;
import com.ctip.application.port.StixObjectPort;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.shared.Visibility;
import com.ctip.domain.stix.StixTlpMarkings;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * GET /api/v1/stix/{stixId} 的查詢(09 §9.1,匿名可存取):
 * marking-definition 由 {@link StixTlpMarkings} 常數供應(固定 OASIS 值,不落 stix_objects);
 * indicator 先經統一可見度(tenant + TLP)與再散布過濾,再回落庫的 content 原文。
 * 跨租戶不可見即查無(API 層映射 404,避免資源存在性洩漏)。
 */
@Service
public class StixQueryService {

    private static final String INDICATOR_PREFIX = "indicator--";

    private final IndicatorRepository indicators;
    private final StixObjectPort stixObjects;
    private final RedistributionFilter redistribution;

    public StixQueryService(
            IndicatorRepository indicators, StixObjectPort stixObjects, RedistributionFilter redistribution) {
        this.indicators = indicators;
        this.stixObjects = stixObjects;
        this.redistribution = redistribution;
    }

    /** marking-definition(常數物件)。 */
    public Optional<Map<String, Object>> findMarking(String stixId) {
        return StixTlpMarkings.markingByStixId(stixId);
    }

    /** indicator 投影的 content JSON 原文;不可見、不可再散布或查無皆為 empty。 */
    public Optional<String> findIndicatorContent(String stixId, Visibility visibility) {
        return indicatorId(stixId)
                .flatMap(id -> indicators.findVisibleById(id, visibility))
                .filter(indicator -> redistribution.redistributableTo(indicator, visibility.viewerTenantId()))
                .flatMap(indicator -> stixObjects.findContent(stixId));
    }

    private static Optional<IndicatorId> indicatorId(String stixId) {
        if (stixId == null || !stixId.startsWith(INDICATOR_PREFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new IndicatorId(UUID.fromString(stixId.substring(INDICATOR_PREFIX.length()))));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
