package com.ctip.infrastructure.persistence;

import com.ctip.application.indicator.IndicatorFilter;
import com.ctip.domain.indicator.IndicatorStatus;
import org.springframework.data.jpa.domain.Specification;

/**
 * IndicatorFilter → JPA Specification(docs/spec/09-api.md §9.5 輸出過濾第 3 步:
 * 預設排除 EXPIRED,除非 includeExpired=true 或明確指定 status)。
 * 第 1–2 步(tenant + TLP)在 TlpSpecifications,兩者一律同時附加。
 */
final class IndicatorFilterSpecs {

    private IndicatorFilterSpecs() {}

    static Specification<IndicatorEntity> matches(IndicatorFilter filter) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
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
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
