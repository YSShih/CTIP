package com.ctip.application.identity;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.RefreshTokenRepository;
import com.ctip.domain.user.RevokedReason;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 過期 refresh token 清理(docs/spec/08-ingestion-sdk.md §8.7,{@code TOKEN_CLEANUP_CRON},每日 02:00)。
 *
 * <p>把 {@code expires_at} 已過而尚未撤銷的 token 標為 {@link RevokedReason#EXPIRED_CLEANUP}。
 * 表 15 早就為這個任務保留了那個 {@code revoked_reason} 值,以及叫 {@code ix_rt_gc} 的
 * {@code expires_at} 索引。
 *
 * <p><strong>過期的 token 本來就用不了</strong>(不變量 U6 的 {@code isUsable} 已經檢查 expiry),
 * 因此這個任務不是安全邊界而是**衛生**:讓「未撤銷」這個狀態真的只代表「還能用」,
 * 撤銷原因欄位因此可以直接回答「這枚是怎麼結束的」。少了它,
 * {@code findActiveByUser} 一類以 {@code revoked_at IS NULL} 為條件的查詢會隨時間累積無用的列。
 *
 * <p><strong>不刪除列</strong>:{@code ip} 與 {@code user_agent} 是個資,移除它們是資料主體刪除
 * (13 §13.4)的職責;而 13 §13.4 的六項保留政策並不含 {@code refresh_tokens}。
 *
 * <p>分批與 {@link com.ctip.application.indicator.IndicatorExpiryService} 同形狀:
 * 單一巨型交易會鎖住整張表,而批次上限是條件寫錯時的護欄,不是正常量。
 */
@Service
public class ExpiredTokenCleanupService {

    private static final int BATCH_SIZE = 1_000;
    private static final int MAX_BATCHES = 1_000;
    private static final Logger log = LoggerFactory.getLogger(ExpiredTokenCleanupService.class);

    private final RefreshTokenRepository tokens;
    private final ClockPort clock;

    public ExpiredTokenCleanupService(RefreshTokenRepository tokens, ClockPort clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    /** @return 本次標記為 EXPIRED_CLEANUP 的筆數 */
    @Transactional
    public int revokeExpiredTokens() {
        Instant now = clock.now();
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            int revoked = tokens.revokeExpired(now, RevokedReason.EXPIRED_CLEANUP, BATCH_SIZE);
            total += revoked;
            if (revoked < BATCH_SIZE) {
                break;
            }
        }
        if (total > 0) {
            log.info("過期 refresh token 清理:{} 筆標記為 EXPIRED_CLEANUP", total);
        }
        return total;
    }
}
