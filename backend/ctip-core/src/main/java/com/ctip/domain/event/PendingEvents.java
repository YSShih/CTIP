package com.ctip.domain.event;

import java.util.ArrayList;
import java.util.List;

/**
 * 聚合內待發佈事件的收集器(組合而非繼承;docs/spec/01-architecture.md §1.7 禁止抽象基底類別)。
 * application service 於交易內 {@link #pull()} 後交給 EventPublisherPort,AFTER_COMMIT 發佈。
 */
public final class PendingEvents {

    private final List<DomainEvent> events = new ArrayList<>();

    public void record(DomainEvent event) {
        events.add(event);
    }

    /** 取出並清空;呼叫端負責發佈。 */
    public List<DomainEvent> pull() {
        List<DomainEvent> pulled = List.copyOf(events);
        events.clear();
        return pulled;
    }
}
