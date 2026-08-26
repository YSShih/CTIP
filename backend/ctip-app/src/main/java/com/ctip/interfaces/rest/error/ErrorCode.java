package com.ctip.interfaces.rest.error;

import org.springframework.http.HttpStatus;

/**
 * 統一錯誤碼(docs/spec/09-api.md §9.4,16 個)。全清單為 API 契約的一部分,
 * 於 Phase 9 一次交付;TOKEN_EXPIRED / SNAPSHOT_REQUIRED / CONFLICT 等由 M2 的
 * 認證與同步流程觸發(§9.4 為跨里程碑契約,非 placeholder)。
 */
public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    INVALID_IOC_FORMAT(HttpStatus.BAD_REQUEST),
    OFFSET_TOO_LARGE(HttpStatus.BAD_REQUEST),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    PLAN_LIMIT_EXCEEDED(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    SNAPSHOT_REQUIRED(HttpStatus.CONFLICT),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    SOURCE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
