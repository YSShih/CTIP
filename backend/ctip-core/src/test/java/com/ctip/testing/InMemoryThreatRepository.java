package com.ctip.testing;

import com.ctip.application.port.ThreatRepository;
import com.ctip.application.threat.ThreatFilter;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.shared.Cursor;
import com.ctip.domain.shared.CursorPage;
import com.ctip.domain.shared.Visibility;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.threat.Threat;
import com.ctip.domain.threat.ThreatId;
import com.ctip.domain.threat.ThreatIndicatorLink;
import com.ctip.domain.threat.ThreatStatus;
import com.ctip.domain.threat.ThreatType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 測試用 in-memory ThreatRepository(application 單元測試;
 * 可見度述詞與 keyset 分頁由 {@code ThreatIntegrationTest} 覆蓋)。
 */
public final class InMemoryThreatRepository implements ThreatRepository {

    private final Map<ThreatId, Threat> store = new LinkedHashMap<>();

    @Override
    public Optional<Threat> findById(ThreatId id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Threat> findVisibleById(ThreatId id, Visibility visibility) {
        return findById(id).filter(threat -> visible(threat, visibility));
    }

    @Override
    public Optional<Threat> findByIdentity(TenantId ownerTenantId, ThreatType type, String name) {
        return store.values().stream()
                .filter(threat -> threat.ownerTenantId().equals(ownerTenantId)
                        && threat.type() == type
                        && threat.name().equals(name))
                .findFirst();
    }

    @Override
    public CursorPage<Threat> findVisible(Visibility visibility, ThreatFilter filter, Cursor after, int limit) {
        List<Threat> visible = store.values().stream()
                .filter(threat -> visible(threat, visibility))
                .filter(threat -> !filter.excludesRetiredByDefault() || threat.status() != ThreatStatus.RETIRED)
                .limit(limit)
                .toList();
        return CursorPage.lastPage(visible);
    }

    @Override
    public List<Threat> findByLinkedIndicator(IndicatorId indicatorId) {
        return store.values().stream()
                .filter(threat -> threat.indicators().stream()
                        .map(ThreatIndicatorLink::indicatorId)
                        .anyMatch(indicatorId::equals))
                .toList();
    }

    @Override
    public Threat save(Threat threat) {
        store.put(threat.id(), threat);
        return threat;
    }

    private static boolean visible(Threat threat, Visibility visibility) {
        if (threat.ownerTenantId().equals(visibility.viewerTenantId())) {
            return true;
        }
        return threat.ownerTenantId().isPublic() && threat.tlp().isNoStricterThan(visibility.maxPublicTlp());
    }
}
