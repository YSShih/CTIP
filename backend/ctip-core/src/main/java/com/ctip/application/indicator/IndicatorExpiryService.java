package com.ctip.application.indicator;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.EventPublisherPort;
import com.ctip.application.port.IndicatorRepository;
import com.ctip.domain.indicator.Indicator;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * IOC 過期標記(docs/spec/07-domain-intel.md §7.10;排程每日 03:00):
 * validUntil 已過的 ACTIVE indicator 轉 EXPIRED 並發 IndicatorExpired 事件。
 * 分批處理避免單一巨型交易;批次上限為防禦性護欄。
 */
@Service
public class IndicatorExpiryService {

    private static final int BATCH_SIZE = 500;
    private static final int MAX_BATCHES = 200;
    private static final Logger log = LoggerFactory.getLogger(IndicatorExpiryService.class);

    private final IndicatorRepository indicators;
    private final EventPublisherPort events;
    private final ClockPort clock;

    public IndicatorExpiryService(IndicatorRepository indicators, EventPublisherPort events, ClockPort clock) {
        this.indicators = indicators;
        this.events = events;
        this.clock = clock;
    }

    /** 回傳本次標記的筆數。單一交易(每日排程,量由 TTL 分散);Spring 代理不支援自我呼叫分交易。 */
    @Transactional
    public int markExpiredIndicators() {
        Instant now = clock.now();
        int total = 0;
        for (int i = 0; i < MAX_BATCHES; i++) {
            int marked = markBatch(now);
            total += marked;
            if (marked < BATCH_SIZE) {
                break;
            }
        }
        if (total > 0) {
            log.info("IOC 過期標記完成:{} 筆", total);
        }
        return total;
    }

    private int markBatch(Instant now) {
        List<Indicator> expirable = indicators.findExpirable(now, BATCH_SIZE);
        for (Indicator indicator : expirable) {
            indicator.markExpired(now);
            indicators.save(indicator);
            indicator.pullEvents().forEach(events::publish);
        }
        return expirable.size();
    }
}
