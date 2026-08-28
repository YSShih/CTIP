package com.ctip.application.ingestion;

/**
 * 提交者要求 {@code CLEAR}/{@code GREEN} 但沒有 {@code ioc:publish}(§9.7)。
 * API 層映射 403 FORBIDDEN——這是權限不足,不是方案能力上限。
 */
public class PublishNotPermittedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PublishNotPermittedException(String message) {
        super(message);
    }
}
