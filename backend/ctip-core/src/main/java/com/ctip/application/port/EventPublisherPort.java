package com.ctip.application.port;

import com.ctip.domain.event.DomainEvent;

/**
 * Domain event 發佈(docs/spec/02-ddd-model.md §2.4)。
 * 實作負責補齊信封欄位(eventId、occurredAt、traceId)並於交易提交後(AFTER_COMMIT)送出;
 * M1–M2 走 ApplicationEventPublisher,M3 另加 Kafka 轉發 listener,發佈端程式碼永不修改。
 */
public interface EventPublisherPort {

    void publish(DomainEvent event);
}
