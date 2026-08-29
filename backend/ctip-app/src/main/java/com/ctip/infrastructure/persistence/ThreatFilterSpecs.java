package com.ctip.infrastructure.persistence;

import com.ctip.application.threat.ThreatFilter;
import com.ctip.domain.threat.ThreatStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * ThreatFilter → JPA Specification(docs/spec/09-api.md §9.1)。
 * 租戶與 TLP 的可見度在 {@code ThreatSpecifications},兩者一律同時附加。
 *
 * <p>{@code tags} 與 {@code aliases} 都是 {@code text[]},一律經自訂 HQL 函式
 * {@code ctip_tags_contain_all}(Postgres 的 {@code @>} + 顯式 {@code cast(? as text[])};
 * 見 {@link PostgresFunctionContributor})。<strong>不得直接把 String[] 綁進 {@code @>}</strong>
 * ——Hibernate 會綁成 {@code varchar[]},PostgreSQL 對 {@code text[] @> varchar[]} 直接報
 * {@code operator does not exist}(13 §13.7 的地雷,Phase 12 已踩過一次)。
 */
final class ThreatFilterSpecs {

    private ThreatFilterSpecs() {}

    static Specification<ThreatEntity> matches(ThreatFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            addEnumPredicates(filter, root, cb, predicates);
            addArrayPredicates(filter, root, cb, predicates);
            if (filter.name() != null) {
                String needle = "%" + escapeLike(filter.name().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), needle, '\\'));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** LIKE 萬用字元跳脫:使用者輸入的 % _ \ 視為字面值(同 PostgresSearchAdapter)。 */
    private static String escapeLike(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static void addEnumPredicates(
            ThreatFilter filter, Root<ThreatEntity> root, CriteriaBuilder cb, List<Predicate> predicates) {
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
        } else if (filter.excludesRetiredByDefault()) {
            predicates.add(cb.notEqual(root.get("status"), ThreatStatus.RETIRED.name()));
        }
    }

    private static void addArrayPredicates(
            ThreatFilter filter, Root<ThreatEntity> root, CriteriaBuilder cb, List<Predicate> predicates) {
        if (!filter.tags().isEmpty()) {
            predicates.add(containsAll(cb, root, "tags", filter.tags()));
        }
        if (!filter.aliases().isEmpty()) {
            predicates.add(containsAll(cb, root, "aliases", filter.aliases()));
        }
    }

    private static Predicate containsAll(
            CriteriaBuilder cb, Root<ThreatEntity> root, String attribute, List<String> values) {
        return cb.isTrue(cb.function(
                "ctip_tags_contain_all",
                Boolean.class,
                root.get(attribute),
                cb.literal(values.toArray(String[]::new))));
    }
}
