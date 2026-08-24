package com.ctip.domain.source;

import java.time.Duration;
import java.time.Instant;

/**
 * 來源健康值物件,狀態機即不變量 S2/S3(docs/spec/02-ddd-model.md):
 * 連續失敗 3 次 → DEGRADED、10 次 → FAILED;任一次成功 → ACTIVE 並歸零。
 * DISABLED 只能由管理員手動進出,不參與自動轉換。
 */
public record SourceHealth(
        SourceStatus status,
        int consecutiveFailures,
        Instant lastSyncAt,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        Integer avgLatencyMs) {

    static final int DEGRADED_THRESHOLD = 3;
    static final int FAILED_THRESHOLD = 10;

    public static SourceHealth initial() {
        return new SourceHealth(SourceStatus.ACTIVE, 0, null, null, null, null);
    }

    public SourceHealth afterSuccess(Instant now, Duration latency) {
        return new SourceHealth(nextStatusAfterSuccess(), 0, now, now, lastFailureAt, movingAverage(latency));
    }

    public SourceHealth afterFailure(Instant now) {
        int failures = consecutiveFailures + 1;
        return new SourceHealth(nextStatusAfterFailure(failures), failures, now, lastSuccessAt, now, avgLatencyMs);
    }

    SourceStatus nextStatusAfterSuccess() {
        return status == SourceStatus.DISABLED ? SourceStatus.DISABLED : SourceStatus.ACTIVE;
    }

    SourceStatus nextStatusAfterFailure(int failures) {
        if (status == SourceStatus.DISABLED) {
            return SourceStatus.DISABLED;
        }
        if (failures >= FAILED_THRESHOLD) {
            return SourceStatus.FAILED;
        }
        if (failures >= DEGRADED_THRESHOLD) {
            return SourceStatus.DEGRADED;
        }
        return status;
    }

    /** 指數移動平均(α = 1/8),確定性且無需保存歷史樣本。 */
    private Integer movingAverage(Duration latency) {
        int sample = (int) Math.min(Integer.MAX_VALUE, latency.toMillis());
        if (avgLatencyMs == null) {
            return sample;
        }
        return avgLatencyMs + (sample - avgLatencyMs) / 8;
    }
}
