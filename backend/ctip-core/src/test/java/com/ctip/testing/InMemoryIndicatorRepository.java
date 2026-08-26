package com.ctip.testing;

import com.ctip.application.indicator.IndicatorFilter;
import com.ctip.application.port.IndicatorRepository;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.shared.Cursor;
import com.ctip.domain.shared.CursorPage;
import com.ctip.domain.shared.Visibility;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.IocType;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 測試用 in-memory IndicatorRepository(pipeline 單元測試;可見度查詢由整合測試覆蓋)。 */
public final class InMemoryIndicatorRepository implements IndicatorRepository {

    private final Map<IndicatorId, Indicator> store = new LinkedHashMap<>();

    @Override
    public Optional<Indicator> findById(IndicatorId id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Indicator> findByIdentity(IocType type, String normalizedValue, TenantId ownerTenantId) {
        return store.values().stream()
                .filter(i -> i.value().type() == type
                        && i.value().normalized().equals(normalizedValue)
                        && i.ownerTenantId().equals(ownerTenantId))
                .findFirst();
    }

    @Override
    public Optional<Indicator> findVisibleById(IndicatorId id, Visibility visibility) {
        return findById(id).filter(i -> i.isVisibleTo(visibility.maxPublicTlp(), visibility.viewerTenantId()));
    }

    @Override
    public Optional<Indicator> findVisibleByIdentity(IocType type, String normalizedValue, Visibility visibility) {
        return store.values().stream()
                .filter(i -> i.value().type() == type
                        && i.value().normalized().equals(normalizedValue)
                        && i.isVisibleTo(visibility.maxPublicTlp(), visibility.viewerTenantId()))
                .findFirst();
    }

    @Override
    public CursorPage<Indicator> findVisible(Visibility visibility, IndicatorFilter filter, Cursor after, int limit) {
        List<Indicator> all = store.values().stream()
                .filter(i -> i.isVisibleTo(visibility.maxPublicTlp(), visibility.viewerTenantId()))
                .limit(limit)
                .toList();
        return new CursorPage<>(all, null, false);
    }

    @Override
    public List<Indicator> findVisibleOffset(Visibility visibility, IndicatorFilter filter, int offset, int limit) {
        return findVisible(visibility, filter, null, offset + limit).items().stream()
                .skip(offset)
                .toList();
    }

    @Override
    public List<Indicator> findExpirable(Instant now, int limit) {
        return store.values().stream()
                .filter(i -> i.status() == com.ctip.domain.indicator.IndicatorStatus.ACTIVE
                        && i.snapshot().validUntil() != null
                        && i.snapshot().validUntil().isBefore(now))
                .limit(limit)
                .toList();
    }

    @Override
    public Indicator save(Indicator indicator) {
        store.put(indicator.id(), indicator);
        return indicator;
    }

    public int size() {
        return store.size();
    }

    public List<Indicator> all() {
        return List.copyOf(store.values());
    }
}
