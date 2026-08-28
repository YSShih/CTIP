package com.ctip.infrastructure.persistence;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.PlanRepository;
import com.ctip.domain.plan.Plan;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.plan.PlanId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * PlanRepository port 的 JPA 實作,附短 TTL 快取。
 *
 * <p>快取在此是必要而非裝飾:{@code RateLimitFilter} 每一個請求都要讀 ANONYMOUS 方案的限額,
 * 分頁夾值也是每次查詢都讀——不快取就是每個請求多兩次 DB 往返,而 {@code plans} 只有四列、
 * 只在部署或 SYSTEM_ADMIN 操作時變動。TTL 取 60 秒:方案調整最多延遲一分鐘生效,
 * 而<strong>訂閱</strong>(哪個租戶用哪個方案)不快取,撤銷立即生效。
 */
@Repository
@Transactional(readOnly = true)
class PlanRepositoryAdapter implements PlanRepository {

    private static final Duration TTL = Duration.ofSeconds(60);

    private final PlanJpaRepository jpa;
    private final PlanMapper mapper;
    private final ClockPort clock;
    private final Map<PlanCode, CachedPlan> cache = new ConcurrentHashMap<>();

    PlanRepositoryAdapter(PlanJpaRepository jpa, PlanMapper mapper, ClockPort clock) {
        this.jpa = jpa;
        this.mapper = mapper;
        this.clock = clock;
    }

    private record CachedPlan(Plan plan, Instant loadedAt) {}

    @Override
    public Optional<Plan> findByCode(PlanCode code) {
        Instant now = clock.now();
        CachedPlan cached = cache.get(code);
        if (cached != null && cached.loadedAt().plus(TTL).isAfter(now)) {
            return Optional.of(cached.plan());
        }
        Optional<Plan> loaded = jpa.findByCode(code.name()).map(mapper::toDomain);
        loaded.ifPresent(plan -> cache.put(code, new CachedPlan(plan, now)));
        return loaded;
    }

    @Override
    public Optional<Plan> findById(PlanId id) {
        return jpa.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<Plan> findAll() {
        return jpa.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional
    public Plan save(Plan plan) {
        PlanEntity entity = jpa.findById(plan.id().value()).orElseGet(PlanEntity::new);
        mapper.updateEntity(plan, entity);
        Plan saved = mapper.toDomain(jpa.save(entity));
        cache.remove(saved.code());
        return saved;
    }
}
