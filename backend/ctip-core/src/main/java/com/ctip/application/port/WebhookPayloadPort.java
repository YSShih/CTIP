package com.ctip.application.port;

import com.ctip.application.notification.NotificationRecord;

/**
 * 送達 body 的序列化(對外線上格式,實作在 {@code interfaces/webhook})。
 *
 * <p>必須是 {@link NotificationRecord} 的<strong>純函數且穩定</strong>:簽章對象含 body,
 * 而重試會在數分鐘後重新組裝同一個事件——欄位順序漂移會讓接收端第二次驗簽失敗。
 */
public interface WebhookPayloadPort {

    byte[] body(NotificationRecord notification);
}
