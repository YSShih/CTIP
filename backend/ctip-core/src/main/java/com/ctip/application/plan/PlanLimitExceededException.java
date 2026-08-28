package com.ctip.application.plan;

/**
 * 非時間窗的方案能力上限(§9.7「配額超限的三種語意」)→ 403 PLAN_LIMIT_EXCEEDED。
 * 這類上限不會自己恢復,等待無用,要解除只能升級方案。
 */
public class PlanLimitExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PlanLimitExceededException(String message) {
        super(message);
    }
}
