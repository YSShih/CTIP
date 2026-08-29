package com.ctip.infrastructure.persistence;

import com.ctip.application.port.CachePort;
import com.ctip.application.port.PlanRepository;
import com.ctip.domain.plan.Plan;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.plan.PlanId;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * PlanRepository port 的 JPA 實作,附短 TTL 快取。
 *
 * <p>快取在此是必要而非裝飾:限流的兩個檢查點<strong>每一個請求</strong>都要讀方案的限額
 * (維度 4 讀 ANONYMOUS、維度 1–3／5 讀呼叫者的方案),分頁夾值也是每次查詢都讀
 * ——不快取就是每個請求多一次 DB 往返,而 {@code plans} 只有四列、只在部署或 SYSTEM_ADMIN
 * 操作時變動。TTL 取 60 秒:方案調整最多延遲一分鐘生效。
 *
 * <p>Phase 17 起改用 {@link CachePort}(原為本類別自有的 {@code ConcurrentHashMap})。
 * 差別不只是少一份程式:行程內的 map <strong>無法跨實例失效</strong>——SYSTEM_ADMIN 在實例 A
 * 調整方案後,實例 B 會繼續用舊配額直到 TTL 到期。{@link #save} 現在做的是分散式失效。
 *
 * <p><strong>訂閱</strong>(哪個租戶用哪個方案)刻意不快取:降級或取消若延遲一分鐘,
 * 那一分鐘內配額仍是舊方案的。
 */
@Repository
@Transactional(readOnly = true)
class PlanRepositoryAdapter implements PlanRepository {

    private static final Duration TTL = Duration.ofSeconds(60);
    private static final String KEY_PREFIX = "plan:code:";

    private final PlanJpaRepository jpa;
    private final PlanMapper mapper;
    private final CachePort cache;

    PlanRepositoryAdapter(PlanJpaRepository jpa, PlanMapper mapper, CachePort cache) {
        this.jpa = jpa;
        this.mapper = mapper;
        this.cache = cache;
    }

    @Override
    public Optional<Plan> findByCode(PlanCode code) {
        Optional<Plan> cached = cache.get(KEY_PREFIX + code.name()).flatMap(PlanCacheCodec::decode);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<Plan> loaded = jpa.findByCode(code.name()).map(mapper::toDomain);
        loaded.ifPresent(plan -> cache.put(KEY_PREFIX + code.name(), PlanCacheCodec.encode(plan), TTL));
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
        cache.evict(KEY_PREFIX + saved.code().name());
        return saved;
    }
}
