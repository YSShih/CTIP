package com.ctip.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.port.ClockPort;
import com.ctip.infrastructure.cache.InMemoryCache;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 同步節流(docs/spec/11-sync-bloom.md §11.6):狀態的存活時間就是該方案的最小間隔,
 * 間隔一過就再也拒絕不了任何請求——逐出因此可以完全交給 TTL。
 */
@Tag("unit")
class CacheBackedSyncThrottleTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-08-29T00:00:00Z"));
    private final ClockPort clock = now::get;
    private final CacheBackedSyncThrottle throttle = new CacheBackedSyncThrottle(new InMemoryCache(clock));

    @Test
    void recordedSyncIsVisibleUntilTheIntervalElapses() {
        Instant at = now.get();
        throttle.recordSync("ip:203.0.113.7", at, Duration.ofMinutes(5));
        assertThat(throttle.lastSyncAt("ip:203.0.113.7")).contains(at);

        now.set(at.plus(Duration.ofMinutes(5)));
        assertThat(throttle.lastSyncAt("ip:203.0.113.7")).isEmpty();
    }

    @Test
    void unknownSubjectHasNeverSynced() {
        assertThat(throttle.lastSyncAt("key:none")).isEmpty();
    }

    /** 間隔為 0 的方案(測試用的 PREMIUM 覆寫)不記帳:TTL 0 在 Redis 是錯誤,而且也無從拒絕。 */
    @Test
    void zeroIntervalIsNotRecorded() {
        throttle.recordSync("ip:203.0.113.7", now.get(), Duration.ZERO);
        assertThat(throttle.lastSyncAt("ip:203.0.113.7")).isEmpty();
    }
}
