package com.ctip.domain.notification;

import java.time.Instant;
import java.util.UUID;

/**
 * domain event 信封裡與領域內容無關的三個欄位(§13.1 規則 4)。
 * 由發佈端補齊,通知投影原樣沿用——特別是 {@code eventId},它是冪等鍵。
 */
public record EventContext(UUID eventId, Instant occurredAt, String traceId) {}
