package com.ctip.testing;

import com.ctip.domain.plan.Plan;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.plan.PlanId;
import com.ctip.domain.plan.QuotaLimit;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * §10.6 配額表的四個方案,逐格謄寫(與 {@code V29__seed_plans_and_permissions.sql} 互為驗證
 * ——種子改了而規格沒改,{@code QuotaEnforcementTest} 會失敗)。
 *
 * <p>刻意每個方案各寫一次完整的建構呼叫、不抽共用 helper:配額表就是逐格對照讀的,
 * 中間隔一層 15 個引數的參數列反而看不出哪一格對到哪一欄。
 * id 由方案代碼決定,測試因此是確定性的。
 */
public final class PlanFixtures {

    private PlanFixtures() {}

    public static PlanId idOf(PlanCode code) {
        return new PlanId(UUID.nameUUIDFromBytes(code.name().getBytes(StandardCharsets.UTF_8)));
    }

    public static Plan of(PlanCode code) {
        return switch (code) {
            case ANONYMOUS -> anonymous();
            case FREE -> free();
            case PREMIUM -> premium();
            case ENTERPRISE -> enterprise();
        };
    }

    private static Plan anonymous() {
        PlanCode code = PlanCode.ANONYMOUS;
        return new Plan(
                idOf(code),
                code,
                "Anonymous",
                code.tier(),
                QuotaLimit.of(60L),
                QuotaLimit.of(1000L),
                50,
                QuotaLimit.of(20L),
                86_400,
                true,
                QuotaLimit.unlimited(),
                false,
                QuotaLimit.disabled(),
                QuotaLimit.disabled(),
                false,
                QuotaLimit.disabled(),
                QuotaLimit.disabled(),
                QuotaLimit.disabled());
    }

    private static Plan free() {
        PlanCode code = PlanCode.FREE;
        return new Plan(
                idOf(code),
                code,
                "Free",
                code.tier(),
                QuotaLimit.of(300L),
                QuotaLimit.of(20_000L),
                100,
                QuotaLimit.of(100L),
                21_600,
                true,
                QuotaLimit.unlimited(),
                false,
                QuotaLimit.disabled(),
                QuotaLimit.of(1L),
                false,
                QuotaLimit.of(1000L),
                QuotaLimit.disabled(),
                QuotaLimit.disabled());
    }

    private static Plan premium() {
        PlanCode code = PlanCode.PREMIUM;
        return new Plan(
                idOf(code),
                code,
                "Premium",
                code.tier(),
                QuotaLimit.of(1200L),
                QuotaLimit.of(500_000L),
                500,
                QuotaLimit.of(1000L),
                300,
                true,
                QuotaLimit.of(1_000_000L),
                true,
                QuotaLimit.of(5L),
                QuotaLimit.of(10L),
                false,
                QuotaLimit.of(50_000L),
                QuotaLimit.of(1000L),
                QuotaLimit.of(10_000L));
    }

    private static Plan enterprise() {
        PlanCode code = PlanCode.ENTERPRISE;
        return new Plan(
                idOf(code),
                code,
                "Enterprise",
                code.tier(),
                QuotaLimit.of(6000L),
                QuotaLimit.unlimited(),
                1000,
                QuotaLimit.of(5000L),
                60,
                true,
                QuotaLimit.of(10_000_000L),
                true,
                QuotaLimit.of(50L),
                QuotaLimit.of(100L),
                true,
                QuotaLimit.unlimited(),
                QuotaLimit.of(50_000L),
                QuotaLimit.of(500_000L));
    }
}
