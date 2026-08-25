package com.ctip.infrastructure.event;

import com.ctip.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * 發佈端補齊的事件信封(docs/spec/02-ddd-model.md §2.4;ADR 0002 決策 2):
 * domain 不得取時間/亂數,eventId 與 occurredAt 由發佈端以 port 產生。
 * M1–M2 經 ApplicationEventPublisher 遞送;M3 由 Kafka listener 消費同一信封。
 */
public record DomainEventEnvelope(UUID eventId, Instant occurredAt, String traceId, DomainEvent event) {}
