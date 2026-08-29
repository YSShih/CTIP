package com.ctip.domain.notification;

/** Webhook 送達結果(docs/spec/04-data-dictionary.md §4.5 的 {@code DeliveryStatus})。 */
public enum DeliveryStatus {
    /** 已建立嘗試列,尚未取得回應。 */
    PENDING,
    /** 2xx。 */
    SUCCESS,
    /** 非 2xx 或連線失敗,仍在重試次數內。 */
    FAILED,
    /** 已用盡不變量 W4 的五次嘗試,不再重試。 */
    ABANDONED
}
