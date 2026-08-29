package com.ctip.domain.notification;

/** Webhook 狀態(docs/spec/04-data-dictionary.md §4.5 的 {@code WebhookStatus})。 */
public enum WebhookStatus {
    /** 正常送達。 */
    ACTIVE,
    /** 由租戶自行暫停;不送達,但不歸零 {@code consecutiveFailures}。 */
    SUSPENDED,
    /** 連續失敗達門檻後由系統停用(不變量 W3);需重新建立。 */
    DISABLED
}
