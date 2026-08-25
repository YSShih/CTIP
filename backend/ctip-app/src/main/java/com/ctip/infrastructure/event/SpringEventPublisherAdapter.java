package com.ctip.infrastructure.event;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.EventPublisherPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.domain.event.DomainEvent;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * EventPublisherPort 的 Spring 實作(docs/spec/08-ingestion-sdk.md §8.2):
 * 補齊信封欄位後於交易 AFTER_COMMIT 發佈;無交易時(如啟動流程)立即發佈。
 * M3 只需新增 Kafka 轉發 listener,本類與所有發佈端程式碼不修改。
 */
@Component
class SpringEventPublisherAdapter implements EventPublisherPort {

    private final ApplicationEventPublisher springEvents;
    private final ClockPort clock;
    private final IdGeneratorPort idGenerator;

    SpringEventPublisherAdapter(ApplicationEventPublisher springEvents, ClockPort clock, IdGeneratorPort idGenerator) {
        this.springEvents = springEvents;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    public void publish(DomainEvent event) {
        DomainEventEnvelope envelope =
                new DomainEventEnvelope(idGenerator.nextId(), clock.now(), MDC.get("traceId"), event);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    springEvents.publishEvent(envelope);
                }
            });
        } else {
            springEvents.publishEvent(envelope);
        }
    }
}
