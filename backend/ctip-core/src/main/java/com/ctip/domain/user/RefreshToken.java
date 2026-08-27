package com.ctip.domain.user;

import java.time.Instant;
import java.util.Objects;

/**
 * User 聚合的內部實體(docs/spec/02-ddd-model.md §2.2)。
 * 不變量 U4:一枚只能使用一次,使用後 usedAt 非 null。原文絕不進入本物件,只保留 SHA-256 雜湊。
 */
public final class RefreshToken {

    private final RefreshTokenId id;
    private final UserId userId;
    private final TokenHash tokenHash;
    private final TokenFamilyId familyId;
    private final RefreshTokenId parentId;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private final String userAgent;
    private final String ip;
    private Instant usedAt;
    private Instant revokedAt;
    private RevokedReason revokedReason;

    private RefreshToken(RefreshTokenSnapshot s) {
        this.id = Objects.requireNonNull(s.id(), "id 不得為 null");
        this.userId = Objects.requireNonNull(s.userId(), "userId 不得為 null");
        this.tokenHash = Objects.requireNonNull(s.tokenHash(), "tokenHash 不得為 null");
        this.familyId = Objects.requireNonNull(s.familyId(), "familyId 不得為 null");
        this.parentId = s.parentId();
        this.issuedAt = Objects.requireNonNull(s.issuedAt(), "issuedAt 不得為 null");
        this.expiresAt = Objects.requireNonNull(s.expiresAt(), "expiresAt 不得為 null");
        this.userAgent = s.userAgent();
        this.ip = s.ip();
        this.usedAt = s.usedAt();
        this.revokedAt = s.revokedAt();
        this.revokedReason = s.revokedReason();
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt 必須晚於 issuedAt");
        }
        if (revokedReason != null && revokedAt == null) {
            throw new IllegalArgumentException("revokedReason 非 null 時 revokedAt 不得為 null");
        }
    }

    public static RefreshToken issue(RefreshTokenSnapshot snapshot) {
        if (snapshot.usedAt() != null || snapshot.revokedAt() != null) {
            throw new IllegalArgumentException("新簽發的 refresh token 不得已使用或已撤銷");
        }
        return new RefreshToken(snapshot);
    }

    /** 由持久化狀態重建(不重放事件,僅重新驗證不變量)。 */
    public static RefreshToken reconstitute(RefreshTokenSnapshot snapshot) {
        return new RefreshToken(snapshot);
    }

    /** 不變量 U4:標記為已使用;重複標記即為重用,由 {@link User} 判定。 */
    void markUsed(Instant now) {
        this.usedAt = Objects.requireNonNull(now, "now 不得為 null");
    }

    /** 不變量 K6 的姊妹規則:已撤銷不可清除,重複撤銷保留最初原因。 */
    void revoke(Instant now, RevokedReason reason) {
        if (revokedAt != null) {
            return;
        }
        this.revokedAt = Objects.requireNonNull(now, "now 不得為 null");
        this.revokedReason = Objects.requireNonNull(reason, "reason 不得為 null");
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    /** 不變量 U6 的判定式:未使用、未撤銷且未過期。 */
    public boolean isUsable(Instant now) {
        return !isUsed() && !isRevoked() && !isExpired(now);
    }

    public RefreshTokenId id() {
        return id;
    }

    public UserId userId() {
        return userId;
    }

    public TokenHash tokenHash() {
        return tokenHash;
    }

    public TokenFamilyId familyId() {
        return familyId;
    }

    public RefreshTokenId parentId() {
        return parentId;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant usedAt() {
        return usedAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public RevokedReason revokedReason() {
        return revokedReason;
    }

    public String userAgent() {
        return userAgent;
    }

    public String ip() {
        return ip;
    }
}
