package com.ctip.domain.event;

import com.ctip.domain.tenant.TenantId;

/**
 * Domain event 的共同型別(docs/spec/02-ddd-model.md §2.4)。
 * 事件記錄「發生了什麼」的領域內容;信封欄位(eventId、occurredAt、traceId)由
 * EventPublisherPort 的實作在發佈時以 ClockPort / IdGeneratorPort 補齊——
 * domain 不得呼叫 Instant.now() / UUID.randomUUID()(ArchUnit 規則 9)。
 */
public interface DomainEvent {

    /** 事件所屬租戶;平台範圍的事件(來源健康、ingestion)為 public tenant。 */
    TenantId tenantId();

    default String eventType() {
        return getClass().getSimpleName();
    }
}
