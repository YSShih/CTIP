package com.ctip.infrastructure.persistence;

import com.ctip.application.port.ThreatRepository;
import com.ctip.application.threat.ThreatFilter;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.shared.Cursor;
import com.ctip.domain.shared.CursorPage;
import com.ctip.domain.shared.Visibility;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.threat.Threat;
import com.ctip.domain.threat.ThreatId;
import com.ctip.domain.threat.ThreatType;
import com.ctip.infrastructure.security.ThreatSpecifications;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * ThreatRepository port 的 JPA 實作。可見性過濾一律經 {@link ThreatSpecifications}
 * (§1.11 唯一一套邏輯);cursor 分頁走 keyset(lastSeen DESC, id DESC),對應
 * {@code ix_threats_last_seen},不使用 COUNT。
 */
@Repository
@Transactional
class ThreatRepositoryAdapter implements ThreatRepository {

    private static final Sort CURSOR_SORT = Sort.by(Sort.Order.desc("lastSeen"), Sort.Order.desc("id"));

    private final ThreatJpaRepository jpa;
    private final ThreatMapper mapper;

    ThreatRepositoryAdapter(ThreatJpaRepository jpa, ThreatMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Threat> findById(ThreatId id) {
        return jpa.findWithDetailsById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Threat> findVisibleById(ThreatId id, Visibility visibility) {
        Specification<ThreatEntity> spec = Specification.allOf(
                (root, query, cb) -> cb.equal(root.get("id"), id.value()), ThreatSpecifications.visibleTo(visibility));
        return jpa.findBy(spec, q -> q.first()).map(entity -> mapper.toDomain(reload(entity)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Threat> findByIdentity(TenantId ownerTenantId, ThreatType type, String name) {
        return jpa.findByOwnerTenantIdAndTypeAndName(ownerTenantId.value(), type.name(), name)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<Threat> findVisible(Visibility visibility, ThreatFilter filter, Cursor after, int limit) {
        Specification<ThreatEntity> spec =
                ThreatSpecifications.visibleTo(visibility).and(ThreatFilterSpecs.matches(filter));
        if (after != null) {
            spec = spec.and(keysetAfter(after));
        }
        List<ThreatEntity> rows =
                jpa.findBy(spec, q -> q.sortBy(CURSOR_SORT).limit(limit + 1).all());
        boolean hasMore = rows.size() > limit;
        List<ThreatEntity> page = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = hasMore ? new Cursor(page.getLast().lastSeen, page.getLast().id).encode() : null;
        return new CursorPage<>(page.stream().map(this::toDomainWithDetails).toList(), nextCursor, hasMore);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Threat> findByLinkedIndicator(IndicatorId indicatorId) {
        return jpa.findByLinkedIndicator(indicatorId.value()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Threat save(Threat threat) {
        ThreatEntity entity = jpa.findWithDetailsById(threat.id().value()).orElseGet(ThreatEntity::new);
        mapper.updateEntity(threat.snapshot(), entity);
        return mapper.toDomain(jpa.save(entity));
    }

    /**
     * Specification 查詢無法帶 {@code @EntityGraph};直接映射會對每個關聯各發一次 lazy 查詢
     * (清單頁 N+1)。改以 id 重新載入同一列的 entity graph——交易內是同一個 persistence context,
     * 不會多打一次主表查詢。
     */
    private Threat toDomainWithDetails(ThreatEntity entity) {
        return mapper.toDomain(reload(entity));
    }

    private ThreatEntity reload(ThreatEntity entity) {
        return jpa.findWithDetailsById(entity.id).orElse(entity);
    }

    /** keyset:(lastSeen, id) 嚴格小於 cursor(對應 ix_threats_last_seen 的排序鍵)。 */
    private static Specification<ThreatEntity> keysetAfter(Cursor after) {
        return (root, query, cb) -> cb.or(
                cb.lessThan(root.get("lastSeen"), after.lastSeen()),
                cb.and(cb.equal(root.get("lastSeen"), after.lastSeen()), cb.lessThan(root.get("id"), after.id())));
    }
}
