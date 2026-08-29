package com.ctip.interfaces.websocket;

import com.ctip.domain.tenant.TenantId;
import java.util.UUID;

/**
 * 一條即時推送連線(WebSocket 或 SSE)。
 *
 * <p>{@link #tenantId()} 是伺服器端過濾的依據——連線在握手時就綁定租戶,client 無從指定,
 * 也無從訂閱別的租戶(09 §9.1「連線綁 tenantId,只推送該租戶可見的事件」)。
 */
interface RealtimeSubscriber {

    /** 連線識別;斷線時以它從登記簿移除。 */
    String id();

    TenantId tenantId();

    /** null 代表 API key 身分(無使用者);指名使用者的通知不會推給它。 */
    UUID userId();

    /** @return false 表示這條連線已經不能用,應由 registry 移除 */
    boolean deliver(String message);

    /** 30 秒心跳(09 §9.1)。@return false 表示連線已死 */
    boolean heartbeat();

    void close();
}
