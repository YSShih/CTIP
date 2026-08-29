package com.ctip.domain.user;

import com.ctip.domain.event.DomainEvent;
import com.ctip.domain.event.PendingEvents;
import com.ctip.domain.event.UserEvents;
import com.ctip.domain.tenant.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * User 聚合根,不變量 U1–U7(docs/spec/02-ddd-model.md §2.3)。
 * U1(email 全域唯一)由 DB 唯一約束強制,小寫由 {@link EmailAddress} 強制;
 * U3 由 {@link PasswordHash} 強制;U4–U6 在 {@link #rotateRefreshToken};U7 在登入計數方法。
 */
public final class User {

    private final PendingEvents pendingEvents = new PendingEvents();

    private final UserId id;
    private final EmailAddress email;
    private final TenantId primaryTenantId;
    private PasswordHash passwordHash;
    private String displayName;
    private UserStatus status;
    private Instant lastLoginAt;
    private int failedLoginCount;
    private Instant lockedUntil;

    private User(UserSnapshot s) {
        this.id = Objects.requireNonNull(s.id(), "id 不得為 null");
        this.email = Objects.requireNonNull(s.email(), "email 不得為 null");
        this.passwordHash = Objects.requireNonNull(s.passwordHash(), "passwordHash 不得為 null");
        this.displayName = s.displayName();
        this.status = Objects.requireNonNull(s.status(), "status 不得為 null");
        this.primaryTenantId = Objects.requireNonNull(s.primaryTenantId(), "primaryTenantId 不得為 null");
        this.lastLoginAt = s.lastLoginAt();
        this.failedLoginCount = s.failedLoginCount();
        this.lockedUntil = s.lockedUntil();
        if (primaryTenantId.isPublic()) {
            throw new IllegalArgumentException("public tenant 不得有使用者(不變量 U2 / T3)");
        }
        if (failedLoginCount < 0) {
            throw new IllegalArgumentException("failedLoginCount 不得為負");
        }
    }

    public static User register(UserSnapshot snapshot) {
        User user = new User(snapshot);
        user.pendingEvents.record(new UserEvents.UserRegistered(user.primaryTenantId, user.id));
        return user;
    }

    /** 由持久化狀態重建(不重放事件,僅重新驗證不變量)。 */
    public static User reconstitute(UserSnapshot snapshot) {
        return new User(snapshot);
    }

    /**
     * 不變量 U4–U6。重用(presented 已使用)一律撤銷整個 family 並發 TokenReuseDetected;
     * 已撤銷／已過期則單純拒絕,不牽連 family。
     *
     * <p>另有一道 family 絕對存活上限:每次輪替都給滿 ttl,沒有上限的話竊得一枚 token 的人
     * 只要持續輪替就能無限期維持存取(ADR 0013)。逾期的 family 整組撤銷。
     */
    public RefreshTokenRotation rotateRefreshToken(RefreshTokenRotationCommand command) {
        RefreshToken presented = command.presented();
        requireOwnedByThisUser(presented);
        if (presented.isUsed()) {
            return revokeFamilyOnReuse(command);
        }
        if (presented.isRevoked() || presented.isExpired(command.at())) {
            return new RefreshTokenRotation(RefreshTokenRotationOutcome.INVALID, null, List.of());
        }
        if (familyOutlivedItsLimit(command)) {
            return new RefreshTokenRotation(
                    RefreshTokenRotationOutcome.INVALID,
                    null,
                    revokeFamily(command.family(), command.at(), RevokedReason.EXPIRED_CLEANUP));
        }
        RefreshToken replacement = command.replacement();
        requireOwnedByThisUser(replacement);
        if (!replacement.familyId().equals(presented.familyId())) {
            throw new IllegalArgumentException("輪替後的 token 必須沿用同一 familyId(不變量 U6)");
        }
        presented.markUsed(command.at());
        presented.revoke(command.at(), RevokedReason.ROTATED);
        return new RefreshTokenRotation(RefreshTokenRotationOutcome.ROTATED, replacement, List.of(presented));
    }

    /** family 的年齡以最早一枚的 issuedAt 起算——輪替鏈上每一枚都算同一個 family 的延續。 */
    private static boolean familyOutlivedItsLimit(RefreshTokenRotationCommand command) {
        TokenFamilyId family = command.presented().familyId();
        return command.family().stream()
                .filter(token -> token.familyId().equals(family))
                .map(RefreshToken::issuedAt)
                .min(Instant::compareTo)
                .map(oldest -> command.at().isAfter(oldest.plus(command.familyMaxLifetime())))
                .orElse(false);
    }

    private RefreshTokenRotation revokeFamilyOnReuse(RefreshTokenRotationCommand command) {
        TokenFamilyId family = command.presented().familyId();
        List<RefreshToken> affected = command.family().stream()
                .filter(token -> token.familyId().equals(family))
                .toList();
        affected.forEach(token -> token.revoke(command.at(), RevokedReason.REUSE_DETECTED));
        pendingEvents.record(new UserEvents.TokenReuseDetected(primaryTenantId, id, family));
        return new RefreshTokenRotation(RefreshTokenRotationOutcome.REUSE_DETECTED, null, affected);
    }

    /** 登出:撤銷指定 family 的全部 token。 */
    public List<RefreshToken> revokeFamily(List<RefreshToken> family, Instant now, RevokedReason reason) {
        family.forEach(token -> token.revoke(now, reason));
        return List.copyOf(family);
    }

    /**
     * 不變量 U7:<strong>連續</strong>失敗達門檻即鎖定。
     *
     * <p>鎖定期滿即開始新的一輪計數。不歸零的話,{@code failedLoginCount} 會永遠停在門檻值,
     * 鎖定過期後<strong>任何一次</strong>失敗都立刻再鎖 15 分鐘——攻擊者每 15 分鐘送一個錯密碼
     * 就能讓受害帳號永久無法登入,而規格說的是「連續失敗 10 次」,不是「一生失敗 10 次」。
     */
    public void recordFailedLogin(Instant now, int maxAttempts, Duration lockDuration) {
        if (lockedUntil != null && !isLocked(now)) {
            failedLoginCount = 0;
            lockedUntil = null;
        }
        failedLoginCount++;
        if (failedLoginCount >= maxAttempts) {
            lockedUntil = now.plus(lockDuration);
        }
    }

    public void recordSuccessfulLogin(Instant now) {
        failedLoginCount = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    /** 不變量 U7:鎖定期間拒絕登入。 */
    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public void changePassword(PasswordHash newHash) {
        this.passwordHash = Objects.requireNonNull(newHash, "passwordHash 不得為 null");
    }

    public void rename(String newDisplayName) {
        this.displayName = newDisplayName;
    }

    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }

    public List<DomainEvent> pullEvents() {
        return pendingEvents.pull();
    }

    private void requireOwnedByThisUser(RefreshToken token) {
        if (!token.userId().equals(id)) {
            throw new IllegalArgumentException("refresh token 不屬於此使用者");
        }
    }

    public UserId id() {
        return id;
    }

    public EmailAddress email() {
        return email;
    }

    public PasswordHash passwordHash() {
        return passwordHash;
    }

    public String displayName() {
        return displayName;
    }

    public UserStatus status() {
        return status;
    }

    public TenantId primaryTenantId() {
        return primaryTenantId;
    }

    public Instant lastLoginAt() {
        return lastLoginAt;
    }

    public int failedLoginCount() {
        return failedLoginCount;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }
}
