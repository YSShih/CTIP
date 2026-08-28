package com.ctip.application.plan;

/**
 * 單次請求的尺寸上限(§9.7「配額超限的三種語意」)→ 413 PAYLOAD_TOO_LARGE。
 * 是「這一次請求太大」,拆小就能過。
 */
public class RequestSizeLimitExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RequestSizeLimitExceededException(String message) {
        super(message);
    }
}
