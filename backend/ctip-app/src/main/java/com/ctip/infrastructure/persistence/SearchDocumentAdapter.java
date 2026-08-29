package com.ctip.infrastructure.persistence;

import com.ctip.application.port.SearchDocumentPort;
import com.ctip.application.port.SearchIndexDocument;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.sdk.RedistributionPolicy;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 搜尋文件的 source of truth 投影(docs/spec/13-platform-ops.md §13.7:
 * 「PostgreSQL 永遠是 source of truth,Elasticsearch 僅為讀取索引,可隨時從 DB 重建」)。
 *
 * <p>軟刪除的 indicator 一律不產生文件——不在索引裡就不可能被查出來。
 */
@Component
@Transactional(readOnly = true)
class SearchDocumentAdapter implements SearchDocumentPort {

    /** 可作為 {@code sourceId} 過濾條件的政策(ADR 0015 修正 2,與 IndicatorFilterSpecs 同一條規則)。 */
    private static final Set<String> DISCLOSABLE = Set.of(
            RedistributionPolicy.PUBLIC_REDISTRIBUTABLE.name(), RedistributionPolicy.ATTRIBUTION_REQUIRED.name());

    private final IndicatorJpaRepository jpa;

    SearchDocumentAdapter(IndicatorJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<SearchIndexDocument> byIds(List<IndicatorId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<UUID> keys = ids.stream().map(IndicatorId::value).toList();
        return jpa.findByDeletedAtIsNullAndIdIn(keys).stream()
                .map(SearchDocumentAdapter::toDocument)
                .toList();
    }

    @Override
    public List<SearchIndexDocument> after(String afterId, int limit) {
        Limit page = Limit.of(limit);
        List<IndicatorEntity> rows = afterId == null
                ? jpa.findByDeletedAtIsNullOrderByIdAsc(page)
                : jpa.findByDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(UUID.fromString(afterId), page);
        return rows.stream().map(SearchDocumentAdapter::toDocument).toList();
    }

    @Override
    public long count() {
        return jpa.countByDeletedAtIsNull();
    }

    private static SearchIndexDocument toDocument(IndicatorEntity entity) {
        Set<String> sourceIds = entity.sources.stream()
                .map(source -> source.sourceId.toString())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> disclosable = entity.sources.stream()
                .filter(source -> DISCLOSABLE.contains(source.redistributionPolicy))
                .map(source -> source.sourceId.toString())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        boolean redistributable = entity.sources.stream()
                .anyMatch(source -> !RedistributionPolicy.INTERNAL_ONLY.name().equals(source.redistributionPolicy));
        return new SearchIndexDocument(
                new IndicatorId(entity.id),
                entity.ownerTenantId.toString(),
                entity.value,
                entity.normalizedValue,
                entity.type,
                entity.severity,
                entity.status,
                entity.tlp,
                entity.confidence,
                entity.score,
                entity.tags == null ? Set.of() : new LinkedHashSet<>(Arrays.asList(entity.tags)),
                entity.firstSeen,
                entity.lastSeen,
                entity.validUntil,
                redistributable,
                sourceIds,
                disclosable,
                entity.updatedAt);
    }
}
