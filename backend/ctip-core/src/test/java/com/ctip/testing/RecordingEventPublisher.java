package com.ctip.testing;

import com.ctip.application.port.EventPublisherPort;
import com.ctip.domain.event.DomainEvent;
import java.util.ArrayList;
import java.util.List;

/** 測試用事件收集器。 */
public final class RecordingEventPublisher implements EventPublisherPort {

    private final List<DomainEvent> published = new ArrayList<>();

    @Override
    public void publish(DomainEvent event) {
        published.add(event);
    }

    public List<DomainEvent> published() {
        return published;
    }
}
