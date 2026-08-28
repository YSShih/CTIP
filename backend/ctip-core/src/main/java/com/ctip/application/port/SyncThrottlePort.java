package com.ctip.application.port;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 「某個 client 上次成功同步 Bloom 的時間」(docs/spec/11-sync-bloom.md §11.6:
 * 同步頻率受 {@code plans.min_sync_interval_seconds} 限制,過於頻繁回 {@code 429})。
 *
 * <p><strong>為什麼不用 {@link RateLimiterPort}</strong>:§10.7 的限流是「視窗內的次數」,
 * 視窗只有 minute / day 兩種({@link RateLimitKey.Window});而 min sync interval 的值是
 * 86400 / 21600 / 300 / 60 秒,其中 6h / 5min / 1min 三種在該列舉裡表達不了,
 * 且它的語意是「距離上次成功同步至少要過多久」而非「每視窗幾次」
 * ——用 1 token 的桶去模擬會在視窗邊界一次放行兩次同步(ADR 0019 已指出這個缺口)。
 *
 * <p>也刻意<strong>不</strong>加資料表:「上次同步時間」是純粹的節流狀態,遺失只會讓某個 client
 * 早一點被允許再同步一次,不影響任何情資正確性;而 {@code sources.last_sync_at} 那種持久化欄位
 * 是給營運觀測用的,per-client(含匿名 IP)的節流狀態寫進資料庫等於為每個 IP 建一列。
 * M2 為單一實例的記憶體實作,Phase 17 隨 Redis 一併換成 {@code SETEX}(TTL = interval,逐出自動發生)。
 */
public interface SyncThrottlePort {

    Optional<Instant> lastSyncAt(String subject);

    /**
     * 記錄一次<strong>成功</strong>的同步。
     *
     * @param minInterval 該方案的最小同步間隔;實作可用它決定何時逐出這筆狀態
     *     ——間隔一過,這筆紀錄就不再能拒絕任何請求
     */
    void recordSync(String subject, Instant at, Duration minInterval);
}
