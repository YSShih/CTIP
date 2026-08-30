package com.ctip.application.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.domain.user.RefreshToken;
import com.ctip.domain.user.RefreshTokenId;
import com.ctip.domain.user.RefreshTokenSnapshot;
import com.ctip.domain.user.RevokedReason;
import com.ctip.domain.user.TokenFamilyId;
import com.ctip.domain.user.TokenHash;
import com.ctip.domain.user.UserId;
import com.ctip.testing.FixedClockPort;
import com.ctip.testing.InMemoryRefreshTokenRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 過期 token 清理(08 §8.7 的 TOKEN_CLEANUP_CRON)。
 * 表 15 早就為它保留了 {@code EXPIRED_CLEANUP} 與 {@code ix_rt_gc},但直到 Phase 23 才有實作。
 */
@Tag("unit")
class ExpiredTokenCleanupServiceTest {

    private static final Instant NOW = FixedClockPort.DEFAULT_NOW;
    private static final UserId USER = new UserId(UUID.fromString("55555555-5555-5555-5555-555555555555"));

    private InMemoryRefreshTokenRepository tokens;
    private ExpiredTokenCleanupService service;

    @BeforeEach
    void setUp() {
        tokens = new InMemoryRefreshTokenRepository();
        service = new ExpiredTokenCleanupService(tokens, FixedClockPort.at(NOW));
    }

    private RefreshToken store(String hash, Instant expiresAt, Instant revokedAt, RevokedReason reason) {
        RefreshToken token = RefreshToken.reconstitute(new RefreshTokenSnapshot(
                new RefreshTokenId(UUID.randomUUID()),
                USER,
                new TokenHash(hash.repeat(64 / hash.length())),
                new TokenFamilyId(UUID.randomUUID()),
                null,
                expiresAt.minus(Duration.ofDays(30)),
                expiresAt,
                null,
                revokedAt,
                reason,
                null,
                null));
        return tokens.save(token);
    }

    @Test
    void marksExpiredTokensAsExpiredCleanup() {
        RefreshToken expired = store("a", NOW.minus(Duration.ofMinutes(1)), null, null);

        assertThat(service.revokeExpiredTokens()).isEqualTo(1);

        RefreshToken after = tokens.findById(expired.id()).orElseThrow();
        assertThat(after.revokedAt()).isEqualTo(NOW);
        assertThat(after.revokedReason()).isEqualTo(RevokedReason.EXPIRED_CLEANUP);
    }

    @Test
    void leavesTokensThatHaveNotExpiredYet() {
        RefreshToken live = store("b", NOW.plus(Duration.ofDays(1)), null, null);

        assertThat(service.revokeExpiredTokens()).isZero();
        assertThat(tokens.findById(live.id()).orElseThrow().revokedAt()).isNull();
    }

    /** 已撤銷不可清除,重複撤銷保留最初原因——這條領域規則由述詞 revoked_at IS NULL 保證。 */
    @Test
    void keepsTheOriginalReasonOfTokensThatWereAlreadyRevoked() {
        Instant loggedOutAt = NOW.minus(Duration.ofDays(2));
        RefreshToken loggedOut = store("c", NOW.minus(Duration.ofMinutes(1)), loggedOutAt, RevokedReason.LOGOUT);

        assertThat(service.revokeExpiredTokens()).isZero();

        RefreshToken after = tokens.findById(loggedOut.id()).orElseThrow();
        assertThat(after.revokedReason()).isEqualTo(RevokedReason.LOGOUT);
        assertThat(after.revokedAt()).isEqualTo(loggedOutAt);
    }

    /** 到期時刻本身算過期(isExpired 是 !expiresAt.isAfter(now))——邊界與領域判定式一致。 */
    @Test
    void treatsTheExpiryInstantItselfAsExpired() {
        store("d", NOW, null, null);

        assertThat(service.revokeExpiredTokens()).isEqualTo(1);
    }

    @Test
    void sweepsEveryExpiredTokenAcrossBatches() {
        for (int i = 0; i < 5; i++) {
            store("e", NOW.minus(Duration.ofHours(i + 1L)), null, null);
        }

        assertThat(service.revokeExpiredTokens()).isEqualTo(5);
        assertThat(tokens.findActiveByUser(USER)).isEmpty();
    }
}
