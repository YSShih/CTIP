package com.ctip.application.ingestion;

import com.ctip.application.port.EventPublisherPort;

/**
 * Stage 10 PublishEvent:取出聚合的待發佈事件交給 EventPublisherPort;
 * 實作於交易 AFTER_COMMIT 送出(§8.2)。M3 轉 Kafka 時本 stage 不修改。
 */
public final class EventPublishStage implements IngestionStage {

    private final EventPublisherPort events;

    public EventPublishStage(EventPublisherPort events) {
        this.events = events;
    }

    @Override
    public String name() {
        return "PublishEvent";
    }

    @Override
    public IngestionContext execute(IngestionContext context) {
        context.indicator().pullEvents().forEach(events::publish);
        return context;
    }
}
