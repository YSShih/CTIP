package com.ctip.infrastructure.persistence;

import com.ctip.application.indicator.IndicatorFilter;
import com.ctip.application.port.IndicatorRepository;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.indicator.IndicatorStatus;
import com.ctip.domain.shared.Cursor;
import com.ctip.domain.shared.CursorPage;
import com.ctip.domain.shared.Visibility;
import com.ctip.domain.tenant.TenantId;
import com.ctip.infrastructure.security.TlpSpecifications;
import com.ctip.sdk.IocType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * IndicatorRepository port 的 JPA 實作。可見性過濾一律經 TlpSpecifications(§1.11 唯一一套邏輯);
 * cursor 分頁走 keyset(lastSeen DESC, id DESC),不使用 COUNT。
 */
@Repository
@Transactional
class IndicatorRepositoryAdapter implements IndicatorRepository {

    private static final Sort CURSOR_SORT = Sort.by(Sort.Order.desc("lastSeen"), Sort.Order.desc("id"));

    private final IndicatorJpaRepository jpa;
    private final IndicatorMapper mapper;
    private final EntityManager entityManager;

    IndicatorRepositoryAdapter(IndicatorJpaRepository jpa, IndicatorMapper mapper, EntityManager entityManager) {
        this.jpa = jpa;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Indicator> findById(IndicatorId id) {
        return jpa.findWithSourcesById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Indicator> findByIdentity(IocType type, String normalizedValue, TenantId ownerTenantId) {
        return jpa.findByTypeAndNormalizedValueAndOwnerTenantId(type.name(), normalizedValue, ownerTenantId.value())
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Indicator> findVisibleById(IndicatorId id, Visibility visibility) {
        Specification<IndicatorEntity> spec = Specification.allOf(
                (root, query, cb) -> cb.equal(root.get("id"), id.value()), TlpSpecifications.visibleTo(visibility));
        return jpa.findBy(spec, q -> q.first()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Indicator> findVisibleByIdentity(IocType type, String normalizedValue, Visibility visibility) {
        Specification<IndicatorEntity> spec = Specification.allOf(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("type"), type.name()),
                        cb.equal(root.get("normalizedValue"), normalizedValue)),
                TlpSpecifications.visibleTo(visibility));
        List<IndicatorEntity> matches = jpa.findBy(spec, q -> q.limit(2).all());
        // 識別鍵含 owner:同值最多命中自家 + public 各一筆,自家優先
        return matches.stream()
                .sorted((a, b) -> Boolean.compare(
                        !a.ownerTenantId.equals(visibility.viewerTenantId().value()),
                        !b.ownerTenantId.equals(visibility.viewerTenantId().value())))
                .findFirst()
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<Indicator> findVisible(Visibility visibility, IndicatorFilter filter, Cursor after, int limit) {
        Specification<IndicatorEntity> spec =
                TlpSpecifications.visibleTo(visibility).and(IndicatorFilterSpecs.matches(filter, visibility));
        if (after != null) {
            spec = spec.and(keysetAfter(after));
        }
        List<IndicatorEntity> rows =
                jpa.findBy(spec, q -> q.sortBy(CURSOR_SORT).limit(limit + 1).all());
        boolean hasMore = rows.size() > limit;
        List<IndicatorEntity> page = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = hasMore ? new Cursor(page.getLast().lastSeen, page.getLast().id).encode() : null;
        return new CursorPage<>(page.stream().map(mapper::toDomain).toList(), nextCursor, hasMore);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Indicator> findVisibleOffset(Visibility visibility, IndicatorFilter filter, int offset, int limit) {
        Specification<IndicatorEntity> spec =
                TlpSpecifications.visibleTo(visibility).and(IndicatorFilterSpecs.matches(filter, visibility));
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<IndicatorEntity> query = cb.createQuery(IndicatorEntity.class);
        Root<IndicatorEntity> root = query.from(IndicatorEntity.class);
        query.where(spec.toPredicate(root, query, cb)).orderBy(cb.desc(root.get("lastSeen")), cb.desc(root.get("id")));
        return entityManager.createQuery(query).setFirstResult(offset).setMaxResults(limit).getResultList().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Indicator> findExpirable(Instant now, int limit) {
        return jpa.findByStatusAndValidUntilBefore(IndicatorStatus.ACTIVE.name(), now, Limit.of(limit)).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Indicator save(Indicator indicator) {
        IndicatorEntity entity = jpa.findWithSourcesById(indicator.id().value()).orElseGet(IndicatorEntity::new);
        mapper.updateEntity(indicator.snapshot(), entity);
        return mapper.toDomain(jpa.save(entity));
    }

    /** keyset:(lastSeen, id) 嚴格小於 cursor(對應 ix_indicators_last_seen 的排序鍵)。 */
    private static Specification<IndicatorEntity> keysetAfter(Cursor after) {
        return (root, query, cb) -> cb.or(
                cb.lessThan(root.get("lastSeen"), after.lastSeen()),
                cb.and(cb.equal(root.get("lastSeen"), after.lastSeen()), cb.lessThan(root.get("id"), after.id())));
    }
}
