package com.ctip.application.identity;

/** 租戶的 API key 數量已達上限(§10.5);對外映射為 {@code PLAN_LIMIT_EXCEEDED}。 */
public class ApiKeyLimitExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ApiKeyLimitExceededException(String message) {
        super(message);
    }
}
