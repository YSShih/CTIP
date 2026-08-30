package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.identity.AuthService;
import com.ctip.application.identity.AuthSession;
import com.ctip.application.identity.ExpiredTokenCleanupService;
import com.ctip.application.port.RefreshTokenRepository;
import com.ctip.application.port.TenantMembershipRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.user.RefreshToken;
import com.ctip.domain.user.RefreshTokenId;
import com.ctip.domain.user.RefreshTokenSnapshot;
import com.ctip.domain.user.RevokedReason;
import com.ctip.domain.user.TokenFamilyId;
import com.ctip.domain.user.TokenHash;
import com.ctip.domain.user.UserId;
import com.ctip.support.TestIdentities;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 過期 refresh token 清理(docs/spec/08-ingestion-sdk.md §8.7 的 {@code TOKEN_CLEANUP_CRON})。
 *
 * <p>驗的是**批次 native UPDATE 的述詞**——in-memory fake 驗不到 SQL 是否真的只打到該打的列。
 * 表 15 的 {@code revoked_reason} 早就保留了 {@code EXPIRED_CLEANUP},索引也叫 {@code ix_rt_gc},
 * 但直到 Phase 23 才有任務去用它們。
 */
class TokenCleanupTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private TenantMembershipRepository memberships;

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Autowired
    private ExpiredTokenCleanupService cleanup;

    private TestIdentities identities;

    @BeforeEach
    void setUp() {
        identities = new TestIdentities(authService, memberships);
    }

    private RefreshToken storeToken(UserId owner, Instant expiresAt, Instant revokedAt, RevokedReason reason) {
        UUID id = UUID.randomUUID();
        return refreshTokens.save(RefreshToken.reconstitute(new RefreshTokenSnapshot(
                new RefreshTokenId(id),
                owner,
                TokenHash.of("cleanup-" + id),
                new TokenFamilyId(UUID.randomUUID()),
                null,
                expiresAt.minus(Duration.ofDays(30)),
                expiresAt,
                null,
                revokedAt,
                reason,
                "junit",
                null)));
    }

    @Test
    void revokesExpiredTokensAndLeavesLiveOnesAlone() {
        AuthSession session = identities.register("token-cleanup@example.org", RoleCode.TENANT_ADMIN);
        UserId owner = session.identity().userId();
        Instant now = Instant.now();
        RefreshToken expired = storeToken(owner, now.minus(Duration.ofHours(1)), null, null);

        assertThat(cleanup.revokeExpiredTokens()).isPositive();

        RefreshToken swept = refreshTokens.findById(expired.id()).orElseThrow();
        assertThat(swept.isRevoked()).isTrue();
        assertThat(swept.revokedReason()).isEqualTo(RevokedReason.EXPIRED_CLEANUP);

        // 註冊當下發的那一枚 30 天後才到期,清理不得碰它——否則每天凌晨兩點全體使用者被登出
        RefreshToken live =
                refreshTokens.findByHash(TokenHash.of(session.refreshToken())).orElseThrow();
        assertThat(live.isRevoked()).isFalse();
        assertThat(refreshTokens.findActiveByUser(owner))
                .extracting(RefreshToken::id)
                .contains(live.id());
    }

    @Test
    void keepsTheOriginalRevocationReason() {
        AuthSession session = identities.register("token-cleanup-revoked@example.org", RoleCode.TENANT_ADMIN);
        Instant loggedOutAt = Instant.now().minus(Duration.ofDays(2));
        RefreshToken loggedOut = storeToken(
                session.identity().userId(),
                Instant.now().minus(Duration.ofHours(1)),
                loggedOutAt,
                RevokedReason.LOGOUT);

        cleanup.revokeExpiredTokens();

        assertThat(refreshTokens.findById(loggedOut.id()).orElseThrow().revokedReason())
                .isEqualTo(RevokedReason.LOGOUT);
    }

    /** 再跑一次不得再動到任何列——述詞若漏了 revoked_at IS NULL,每次清理都會重寫全表。 */
    @Test
    void isIdempotent() {
        AuthSession session = identities.register("token-cleanup-idempotent@example.org", RoleCode.TENANT_ADMIN);
        storeToken(session.identity().userId(), Instant.now().minus(Duration.ofHours(1)), null, null);

        assertThat(cleanup.revokeExpiredTokens()).isPositive();
        assertThat(cleanup.revokeExpiredTokens()).isZero();
    }
}
