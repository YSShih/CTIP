package com.ctip.application.port;

import com.ctip.application.source.SourceSyncReport;
import com.ctip.domain.source.SourceId;
import java.time.Instant;
import java.util.UUID;

/**
 * source_sync 的寫入 port(兩模型表):start 建立 RUNNING 列,finish 回寫一次結果——
 * 之後不再更新(finished_at 為 null 表示仍在執行或異常中斷)。
 */
public interface SourceSyncLogPort {

    UUID start(SourceId sourceId, Instant startedAt);

    void finish(SourceSyncReport report);
}
