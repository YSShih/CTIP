package com.ctip.testing;

import com.ctip.application.port.SyncThrottlePort;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** 測試用的同步節流狀態(11 §11.6);正式實作的逐出行為屬 infrastructure,此處只記最後一次。 */
public final class FakeSyncThrottle implements SyncThrottlePort {

    private final Map<String, Instant> lastSync = new HashMap<>();
    private final Map<String, Duration> intervals = new HashMap<>();

    @Override
    public Optional<Instant> lastSyncAt(String subject) {
        return Optional.ofNullable(lastSync.get(subject));
    }

    @Override
    public void recordSync(String subject, Instant at, Duration minInterval) {
        lastSync.put(subject, at);
        intervals.put(subject, minInterval);
    }

    /** 測試斷言用:呼叫端記帳時傳進來的間隔(應等於該方案的 min_sync_interval_seconds)。 */
    public Optional<Duration> intervalOf(String subject) {
        return Optional.ofNullable(intervals.get(subject));
    }
}
