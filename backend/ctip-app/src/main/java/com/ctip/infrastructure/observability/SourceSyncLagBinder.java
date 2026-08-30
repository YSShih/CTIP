package com.ctip.infrastructure.observability;

import com.ctip.application.observability.CtipMetricNames;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.SourceRepository;
import com.ctip.domain.source.Source;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code ctip.source.sync.lag{source}}(docs/spec/13-platform-ops.md §13.6):
 * 距上次<strong>成功</strong>同步的秒數。從未成功過的來源回 {@code NaN} 而不是 0——
 * 0 的意思是「剛剛才同步過」,對從未成功的來源是相反的結論。
 *
 * <p>來源集合會變(管理端點可新增/停用),所以用 {@link MultiGauge};
 * 啟動時綁一次,之後由 {@code MetricsSchedulers} 定期重整。
 */
public class SourceSyncLagBinder implements MeterBinder {

    private static final Logger log = LoggerFactory.getLogger(SourceSyncLagBinder.class);

    private final SourceRepository sources;
    private final ClockPort clock;
    private MultiGauge gauges;

    public SourceSyncLagBinder(SourceRepository sources, ClockPort clock) {
        this.sources = sources;
        this.clock = clock;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        this.gauges = MultiGauge.builder(CtipMetricNames.SOURCE_SYNC_LAG)
                .description("距上次成功同步的秒數")
                .baseUnit("seconds")
                .register(registry);
        refresh();
    }

    /** 重新掃描來源清單。資料庫暫時不可用不得讓指標蒐集炸掉整個抓取。 */
    public void refresh() {
        if (gauges == null) {
            return;
        }
        try {
            List<MultiGauge.Row<?>> rows = new ArrayList<>();
            for (Source source : sources.findAll()) {
                // 值以函式登記而非固定數字:抓取時才算,落後秒數因此在兩次重整之間也持續增加
                rows.add(MultiGauge.Row.of(
                        Tags.of("source", source.snapshot().sourceType().name()),
                        source,
                        s -> lagSeconds(s, clock.now())));
            }
            gauges.register(rows, true);
        } catch (RuntimeException e) {
            log.warn("ctip.source.sync.lag 重整失敗,沿用上一輪的值", e);
        }
    }

    private static double lagSeconds(Source source, Instant now) {
        Instant lastSuccess = source.health().lastSuccessAt();
        if (lastSuccess == null) {
            return Double.NaN;
        }
        return Math.max(0, Duration.between(lastSuccess, now).toSeconds());
    }
}
