package com.ctip.application.stix;

import com.ctip.application.indicator.RedistributionFilter;
import com.ctip.application.port.IndicatorRepository;
import com.ctip.application.port.StixObjectPort;
import com.ctip.application.port.StixRelationshipPort;
import com.ctip.application.port.ThreatRepository;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.shared.Visibility;
import com.ctip.domain.stix.StixOrigin;
import com.ctip.domain.stix.StixRelationship;
import com.ctip.domain.stix.StixRelationshipProjector;
import com.ctip.domain.stix.StixTlpMarkings;
import com.ctip.domain.threat.Threat;
import com.ctip.domain.threat.ThreatId;
import com.ctip.domain.threat.ThreatIndicatorLink;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * GET /api/v1/stix/{stixId} 的查詢(09 §9.1,匿名可存取)。
 *
 * <p>marking-definition 由 {@link StixTlpMarkings} 常數供應(固定 OASIS 值,不落 stix_objects);
 * 其餘物件一律先過可見度,再回落庫的 content 原文。可見度依<strong>來源 domain 物件</strong>判定,
 * 不看 STIX 型別:
 * <ul>
 *   <li>來源是 Indicator({@code indicator}、{@code observed-data}):統一可見度 + 再散布過濾</li>
 *   <li>來源是 Threat({@code malware}、{@code attack-pattern}):threats 的可見度述詞</li>
 *   <li>沒有來源聚合({@code identity} ← Source):情資提供方的身分不是情資,公開</li>
 *   <li>{@code relationship}:兩端都必須可見——關聯本身會洩漏「某個私有 IOC 屬於某個公開威脅」</li>
 * </ul>
 * 跨租戶不可見即查無(API 層映射 404,避免資源存在性洩漏)。
 */
@Service
public class StixQueryService {

    private static final String RELATIONSHIP_PREFIX = "relationship--";

    private final IndicatorRepository indicators;
    private final ThreatRepository threats;
    private final StixObjectPort stixObjects;
    private final StixRelationshipPort stixRelationships;
    private final RedistributionFilter redistribution;

    public StixQueryService(
            IndicatorRepository indicators,
            ThreatRepository threats,
            StixObjectPort stixObjects,
            StixRelationshipPort stixRelationships,
            RedistributionFilter redistribution) {
        this.indicators = indicators;
        this.threats = threats;
        this.stixObjects = stixObjects;
        this.stixRelationships = stixRelationships;
        this.redistribution = redistribution;
    }

    /** marking-definition(常數物件)。 */
    public Optional<Map<String, Object>> findMarking(String stixId) {
        return StixTlpMarkings.markingByStixId(stixId);
    }

    /** stix_objects 落庫的 content JSON 原文;不可見、不可再散布或查無皆為 empty。 */
    public Optional<String> findContent(String stixId, Visibility visibility) {
        if (stixId == null || stixId.startsWith(RELATIONSHIP_PREFIX)) {
            return Optional.empty();
        }
        return stixObjects
                .findOrigin(stixId)
                .filter(origin -> originVisible(origin, visibility))
                .flatMap(origin -> stixObjects.findContent(stixId));
    }

    private boolean originVisible(StixOrigin origin, Visibility visibility) {
        if (origin.isStandalone()) {
            return true;
        }
        if (origin.threatId() != null) {
            return threats.findVisibleById(origin.threatId(), visibility).isPresent();
        }
        return indicators
                .findVisibleById(origin.indicatorId(), visibility)
                .filter(indicator -> redistribution.redistributableTo(indicator, visibility.viewerTenantId()))
                .isPresent();
    }

    /**
     * 表 9 沒有 content 欄:JSON 由落庫的三元組 + Threat 聚合(角色來自 threat_indicators)重建,
     * created/modified 取自那一列,重建不會漂移。序列化留在 interfaces 層(core 不碰 JSON)。
     */
    public Optional<Map<String, Object>> findRelationship(String stixId, Visibility visibility) {
        if (stixId == null || !stixId.startsWith(RELATIONSHIP_PREFIX)) {
            return Optional.empty();
        }
        return stixRelationships
                .findByStixId(stixId)
                .flatMap(relationship -> visibleRelationshipContent(relationship, visibility));
    }

    private Optional<Map<String, Object>> visibleRelationshipContent(
            StixRelationship relationship, Visibility visibility) {
        Optional<Threat> threat =
                threatOf(relationship.targetRef()).flatMap(id -> threats.findVisibleById(id, visibility));
        if (threat.isEmpty()) {
            return Optional.empty();
        }
        Optional<ThreatIndicatorLink> link = threat.get().indicators().stream()
                .filter(candidate ->
                        StixRelationshipProjector.sourceRef(candidate).equals(relationship.sourceRef()))
                .findFirst();
        if (link.isEmpty() || !indicatorVisible(link.get(), visibility)) {
            return Optional.empty();
        }
        return Optional.of(StixRelationshipProjector.content(
                threat.get().snapshot(), link.get(), relationship.created(), relationship.modified()));
    }

    private boolean indicatorVisible(ThreatIndicatorLink link, Visibility visibility) {
        Optional<Indicator> indicator = indicators.findVisibleById(link.indicatorId(), visibility);
        return indicator
                .filter(found -> redistribution.redistributableTo(found, visibility.viewerTenantId()))
                .isPresent();
    }

    private static Optional<ThreatId> threatOf(String targetRef) {
        int separator = targetRef.indexOf("--");
        if (separator < 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ThreatId(UUID.fromString(targetRef.substring(separator + 2))));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
