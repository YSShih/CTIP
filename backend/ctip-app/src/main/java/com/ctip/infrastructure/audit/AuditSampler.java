package com.ctip.infrastructure.audit;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 取樣(docs/spec/13-platform-ops.md §13.5 規則 4:寫入操作 100%、讀取操作 1%,
 * 比率可由 {@code AUDIT_SAMPLE_READ_RATE} 設定)。
 *
 * <p>1.0 表示全記(整合測試用這個值:取樣是機率,測試不能靠機率)。
 */
public class AuditSampler {

    private final double readRate;

    public AuditSampler(double readRate) {
        if (readRate < 0 || readRate > 1) {
            throw new IllegalArgumentException("稽核讀取取樣率必須介於 0 與 1:" + readRate);
        }
        this.readRate = readRate;
    }

    public boolean keepRead() {
        if (readRate >= 1) {
            return true;
        }
        return readRate > 0 && ThreadLocalRandom.current().nextDouble() < readRate;
    }
}
