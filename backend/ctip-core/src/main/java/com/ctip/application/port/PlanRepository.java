package com.ctip.application.port;

import com.ctip.domain.plan.Plan;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.plan.PlanId;
import java.util.List;
import java.util.Optional;

/**
 * 方案定義 port(docs/spec/04-data-dictionary.md 表 17)。
 * 配額值一律由此讀取,不得 hard-code(§10.6)。
 */
public interface PlanRepository {

    Optional<Plan> findByCode(PlanCode code);

    Optional<Plan> findById(PlanId id);

    List<Plan> findAll();

    /** CTIP_PLAN_OVERRIDES 於啟動時套用部署期覆寫(§10.6;ADR 0019)。 */
    Plan save(Plan plan);
}
