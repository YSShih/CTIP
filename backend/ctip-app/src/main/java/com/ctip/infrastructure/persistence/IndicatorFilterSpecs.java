package com.ctip.infrastructure.persistence;

import com.ctip.application.indicator.IndicatorFilter;
import com.ctip.domain.indicator.IndicatorStatus;
import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * IndicatorFilter → JPA Specification(docs/spec/09-api.md §9.5 輸出過濾第 3 步:
 * 預設排除 EXPIRED,除非 includeExpired=true 或明確指定 status;13 §13.7 搜尋欄位)。
 * 第 1–2 步(tenant + TLP)在 TlpSpecifications,兩者一律同時附加。
 * tags 以自訂 HQL 函式 ctip_tags_contain_all(Postgres `@>` + text[] cast,
 * 見 PostgresFunctionContributor)實作,吃 ix_indicators_tags GIN 索引。
 */
final class IndicatorFilterSpecs {

    private IndicatorFilterSpecs() {}

    static Specification<IndicatorEntity> matches(IndicatorFilter filter) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            addEnumPredicates(filter, root, cb, predicates);
            if (!filter.tags().isEmpty()) {
                predicates.add(cb.isTrue(cb.function(
                        "ctip_tags_contain_all",
                        Boolean.class,
                        root.get("tags"),
                        cb.literal(filter.tags().toArray(String[]::new)))));
            }
            if (filter.sourceId() != null) {
                predicates.add(cb.exists(reportedBySource(root, query, cb, filter)));
            }
            addRangePredicates(filter, root, cb, predicates);
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static void addEnumPredicates(
            IndicatorFilter filter, Root<IndicatorEntity> root, CriteriaBuilder cb, List<Predicate> predicates) {
        if (filter.type() != null) {
            predicates.add(cb.equal(root.get("type"), filter.type().name()));
        }
        if (filter.severity() != null) {
            predicates.add(cb.equal(root.get("severity"), filter.severity().name()));
        }
        if (filter.tlp() != null) {
            predicates.add(cb.equal(root.get("tlp"), filter.tlp().name()));
        }
        if (filter.status() != null) {
            predicates.add(cb.equal(root.get("status"), filter.status().name()));
        } else if (filter.excludesExpiredByDefault()) {
            predicates.add(cb.notEqual(root.get("status"), IndicatorStatus.EXPIRED.name()));
        }
    }

    private static void addRangePredicates(
            IndicatorFilter filter, Root<IndicatorEntity> root, CriteriaBuilder cb, List<Predicate> predicates) {
        if (filter.confidence().min() != null) {
            predicates.add(cb.ge(root.get("confidence"), filter.confidence().min()));
        }
        if (filter.confidence().max() != null) {
            predicates.add(cb.le(root.get("confidence"), filter.confidence().max()));
        }
        if (filter.score().min() != null) {
            predicates.add(cb.ge(root.get("score"), filter.score().min()));
        }
        if (filter.score().max() != null) {
            predicates.add(cb.le(root.get("score"), filter.score().max()));
        }
        if (filter.lastSeen().from() != null) {
            predicates.add(cb.greaterThanOrEqualTo(
                    root.get("lastSeen"), filter.lastSeen().from()));
        }
        if (filter.lastSeen().to() != null) {
            predicates.add(
                    cb.lessThanOrEqualTo(root.get("lastSeen"), filter.lastSeen().to()));
        }
    }

    private static Subquery<Integer> reportedBySource(
            Root<IndicatorEntity> root, CommonAbstractCriteria query, CriteriaBuilder cb, IndicatorFilter filter) {
        Subquery<Integer> sub = query.subquery(Integer.class);
        Root<IndicatorSourceEntity> source = sub.from(IndicatorSourceEntity.class);
        return sub.select(cb.literal(1))
                .where(cb.equal(source.get("indicator"), root), cb.equal(source.get("sourceId"), filter.sourceId()));
    }
}
