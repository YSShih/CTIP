package com.ctip.application.notification;

/**
 * 一次 HTTP 送達的結果。
 *
 * @param httpStatus null 代表連線層面失敗(逾時、DNS、TLS),沒有取得任何回應碼
 * @param errorMessage 失敗原因;<strong>不得含密鑰或完整 payload</strong>,它會落進 append-only 的送達記錄
 */
public record WebhookSendResult(boolean success, Integer httpStatus, int responseTimeMs, String errorMessage) {

    public static WebhookSendResult ok(int httpStatus, int responseTimeMs) {
        return new WebhookSendResult(true, httpStatus, responseTimeMs, null);
    }

    public static WebhookSendResult rejected(int httpStatus, int responseTimeMs) {
        return new WebhookSendResult(false, httpStatus, responseTimeMs, "HTTP " + httpStatus);
    }

    public static WebhookSendResult failed(int responseTimeMs, String errorMessage) {
        return new WebhookSendResult(false, null, responseTimeMs, errorMessage);
    }
}
