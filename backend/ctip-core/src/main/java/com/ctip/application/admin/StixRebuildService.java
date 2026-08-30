package com.ctip.application.admin;

import com.ctip.application.port.IndicatorRepository;
import com.ctip.application.stix.StixProjectionFactory;
import com.ctip.application.stix.StixProjectionWriter;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 由 domain 重建全部 STIX 投影({@code POST /api/v1/admin/stix/rebuild};§9.1「管理」)。
 *
 * <p>{@code stix_objects} 是<strong>衍生</strong>資料(§7.8.6),隨時可由 indicators 重算。
 * 需要它的情況:投影寫出曾經失敗(§7.8.6 的失敗隔離會跳過那一筆)、或投影規則變更後要回填。
 *
 * <p>逐批處理、每批一個交易:全庫一個交易在真實資料量下會鎖住 indicators。
 * 寫出沿用 {@link StixProjectionWriter} 的逐筆失敗隔離——一筆壞資料不該讓整次重建停擺。
 */
@Service
public class StixRebuildService {

    private static final int BATCH_SIZE = 200;

    private static final Logger log = LoggerFactory.getLogger(StixRebuildService.class);

    private final IndicatorRepository indicators;
    private final StixProjectionFactory projections;
    private final StixProjectionWriter writer;

    public StixRebuildService(
            IndicatorRepository indicators, StixProjectionFactory projections, StixProjectionWriter writer) {
        this.indicators = indicators;
        this.projections = projections;
        this.writer = writer;
    }

    /** @return 重投影的 indicator 筆數 */
    public int rebuildAll() {
        int rebuilt = 0;
        IndicatorId after = null;
        List<Indicator> batch = rebuildBatch(after);
        while (!batch.isEmpty()) {
            rebuilt += batch.size();
            after = batch.get(batch.size() - 1).id();
            batch = rebuildBatch(after);
        }
        log.info("STIX 重建完成:{} 筆 indicator", rebuilt);
        return rebuilt;
    }

    /**
     * 一批。<strong>刻意不標 {@code @Transactional}</strong>:同類別內的自我呼叫走不到 proxy,
     * 標了也不會生效(那會是一條看起來有、實際沒有的交易邊界)。批次內的每次讀寫
     * 各自由 repository adapter 的交易邊界涵蓋,而重建本來就是可重跑的冪等操作。
     */
    private List<Indicator> rebuildBatch(IndicatorId after) {
        List<Indicator> batch = indicators.findAllAfter(after, BATCH_SIZE);
        batch.forEach(indicator -> writer.writeAll(projections.projectionsFor(indicator.snapshot())));
        return batch;
    }
}
