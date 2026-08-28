package com.ctip.testing;

import com.ctip.application.port.PlanRepository;
import com.ctip.domain.plan.Plan;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.plan.PlanId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 測試用 plans 表;預設載入 §10.6 的四個方案。 */
public final class InMemoryPlanRepository implements PlanRepository {

    private final Map<PlanCode, Plan> plans = new EnumMap<>(PlanCode.class);

    public InMemoryPlanRepository() {
        for (PlanCode code : PlanCode.values()) {
            plans.put(code, PlanFixtures.of(code));
        }
    }

    /** 讓個別測試改寫某個方案的配額(例:把 FREE 的每日提交上限調成 2 以測用罄)。 */
    public void put(Plan plan) {
        plans.put(plan.code(), plan);
    }

    @Override
    public Optional<Plan> findByCode(PlanCode code) {
        return Optional.ofNullable(plans.get(code));
    }

    @Override
    public Optional<Plan> findById(PlanId id) {
        return plans.values().stream().filter(plan -> plan.id().equals(id)).findFirst();
    }

    @Override
    public List<Plan> findAll() {
        return List.copyOf(plans.values());
    }

    @Override
    public Plan save(Plan plan) {
        plans.put(plan.code(), plan);
        return plan;
    }
}
