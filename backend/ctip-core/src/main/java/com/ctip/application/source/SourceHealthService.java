package com.ctip.application.source;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.EventPublisherPort;
import com.ctip.application.port.SourceRepository;
import com.ctip.domain.source.Source;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 來源健康記錄(docs/spec/08-ingestion-sdk.md §8.6):狀態機在 {@link Source} 聚合內
 * (S2–S4),本 service 負責交易邊界、持久化與事件發佈。抓取本身絕不在此交易內。
 */
@Service
public class SourceHealthService {

    private final SourceRepository sources;
    private final EventPublisherPort events;
    private final ClockPort clock;

    public SourceHealthService(SourceRepository sources, EventPublisherPort events, ClockPort clock) {
        this.sources = sources;
        this.events = events;
        this.clock = clock;
    }

    /** 同步成功:健康歸零、累計筆數、保存續抓游標。 */
    @Transactional
    public void recordSuccess(Source source, int recordCount, Duration latency, String nextCursor) {
        source.recordSuccess(recordCount, latency, clock.now());
        source.advanceCursor(nextCursor);
        saveAndPublish(source);
    }

    /** 同步失敗:計數遞增(3 → DEGRADED、10 → FAILED);錯誤訊息由聚合遮罩(S5)。 */
    @Transactional
    public void recordFailure(Source source, String reason) {
        source.recordFailure(reason, clock.now());
        saveAndPublish(source);
    }

    private void saveAndPublish(Source source) {
        sources.save(source);
        source.pullEvents().forEach(events::publish);
    }
}
