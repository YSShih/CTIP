package com.ctip.application.notification;

import com.ctip.domain.shared.Cursor;
import com.ctip.domain.tenant.TenantId;
import java.util.UUID;

/**
 * 通知中心的查詢條件(09 §9.1 的 {@code GET /notifications})。
 *
 * <p>可見範圍固定為「本租戶 + public tenant 的平台通知」,再加上「廣播列或指定給我的列」
 * ——與 §7.9 的 {@code IN (current, public)} 同一條規則,由 port 實作強制,呼叫端不得指定租戶。
 *
 * @param unreadOnly 只回未讀
 */
public record NotificationQuery(TenantId tenantId, UUID userId, boolean unreadOnly, Cursor cursor, int limit) {}
