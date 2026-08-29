package com.ctip.interfaces.webhook;

/** §13.2 的五個送達標頭。名稱是對外契約,接收端照這幾個字串驗簽。 */
public final class WebhookHeaders {

    public static final String SIGNATURE = "X-CTIP-Signature";
    public static final String EVENT_ID = "X-CTIP-Event-Id";
    public static final String EVENT_TYPE = "X-CTIP-Event-Type";
    public static final String DELIVERY_ATTEMPT = "X-CTIP-Delivery-Attempt";
    public static final String TIMESTAMP = "X-CTIP-Timestamp";

    private WebhookHeaders() {}
}
