package com.ctip.infrastructure.persistence;

import com.ctip.application.port.StatsPort;
import com.ctip.domain.indicator.IndicatorStatus;
import com.ctip.domain.shared.Visibility;
import com.ctip.domain.source.SourceId;
import com.ctip.infrastructure.security.TlpSpecifications;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard 統計(docs/spec/09-api.md §9.1 /stats/*)。
 * summary 的可見度重用 TlpSpecifications(§1.11 唯一一套過濾邏輯,不另寫 SQL 複本);
 * 統計口徑:可見且 status=ACTIVE(未過期)的 indicator。
 */
@Component
@Transactional(readOnly = true)
class StatsAdapter implements StatsPort {

    private static final int TREND_DAYS = 7;

    private final EntityManager entityManager;
    private final SourceJpaRepository sources;

    StatsAdapter(EntityManager entityManager, SourceJpaRepository sources) {
        this.entityManager = entityManager;
        this.sources = sources;
    }

    @Override
    public StatsSummary summary(Visibility visibility, Instant now) {
        Map<String, Long> byType = countByType(visibility);
        long total = byType.values().stream().mapToLong(Long::longValue).sum();
        return new StatsSummary(total, byType, trend(visibility, now));
    }

    private Map<String, Long> countByType(Visibility visibility) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<IndicatorEntity> root = query.from(IndicatorEntity.class);
        query.multiselect(root.get("type"), cb.count(root))
                .where(activeAndVisible(visibility, root, query, cb))
                .groupBy(root.get("type"));
        Map<String, Long> byType = new LinkedHashMap<>();
        for (Tuple row : entityManager.createQuery(query).getResultList()) {
            byType.put(row.get(0, String.class), row.get(1, Long.class));
        }
        return byType;
    }

    /** 近 7 日(依 lastSeen 的 UTC 日期)每日筆數;無資料的日期補 0。 */
    private List<DailyCount> trend(Visibility visibility, Instant now) {
        Instant windowStart =
                now.minus(Duration.ofDays(TREND_DAYS - 1)).truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<IndicatorEntity> root = query.from(IndicatorEntity.class);
        // date_trunc 對 timestamptz 依 session TimeZone 切日(JDBC 連線帶 JVM 時區);
        // 必須先 timezone('UTC', …) 再取日期字串,否則非 UTC 環境會把 UTC 16:00–24:00
        // 的資料切到鄰日、掉出 7 日窗(與下方以 UTC 對桶的 Java 端不一致)
        Expression<String> day = cb.function(
                "to_char",
                String.class,
                cb.function("timezone", Instant.class, cb.literal("UTC"), root.get("lastSeen")),
                cb.literal("YYYY-MM-DD"));
        query.multiselect(day, cb.count(root))
                .where(cb.and(
                        activeAndVisible(visibility, root, query, cb),
                        cb.greaterThanOrEqualTo(root.get("lastSeen"), windowStart)))
                .groupBy(day);
        Map<LocalDate, Long> counted = new HashMap<>();
        for (Tuple row : entityManager.createQuery(query).getResultList()) {
            counted.put(LocalDate.parse(row.get(0, String.class)), row.get(1, Long.class));
        }
        List<DailyCount> trend = new ArrayList<>(TREND_DAYS);
        LocalDate first = LocalDate.ofInstant(windowStart, ZoneOffset.UTC);
        for (int i = 0; i < TREND_DAYS; i++) {
            LocalDate date = first.plusDays(i);
            trend.add(new DailyCount(date, counted.getOrDefault(date, 0L)));
        }
        return trend;
    }

    private static Predicate activeAndVisible(
            Visibility visibility, Root<IndicatorEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Predicate visible = TlpSpecifications.visibleTo(visibility).toPredicate(root, query, cb);
        return cb.and(visible, cb.equal(root.get("status"), IndicatorStatus.ACTIVE.name()));
    }

    @Override
    public List<SourceStats> sources() {
        Map<UUID, Long> counts = new HashMap<>();
        List<Tuple> rows = entityManager
                .createQuery(
                        "SELECT r.sourceId AS sourceId, COUNT(r) AS total FROM IndicatorSourceEntity r"
                                + " GROUP BY r.sourceId",
                        Tuple.class)
                .getResultList();
        rows.forEach(row -> counts.put(row.get("sourceId", UUID.class), row.get("total", Long.class)));
        return sources.findAll().stream()
                .map(entity -> new SourceStats(
                        new SourceId(entity.id),
                        entity.sourceType,
                        entity.displayName,
                        entity.status,
                        entity.enabled,
                        counts.getOrDefault(entity.id, 0L),
                        entity.lastSuccessAt))
                .toList();
    }
}
