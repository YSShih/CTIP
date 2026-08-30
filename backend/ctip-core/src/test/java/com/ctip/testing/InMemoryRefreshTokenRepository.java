package com.ctip.testing;

import com.ctip.application.port.RefreshTokenRepository;
import com.ctip.domain.user.RefreshToken;
import com.ctip.domain.user.RefreshTokenId;
import com.ctip.domain.user.RefreshTokenSnapshot;
import com.ctip.domain.user.RevokedReason;
import com.ctip.domain.user.TokenFamilyId;
import com.ctip.domain.user.TokenHash;
import com.ctip.domain.user.UserId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 測試用 in-memory RefreshTokenRepository。 */
public final class InMemoryRefreshTokenRepository implements RefreshTokenRepository {

    private final Map<RefreshTokenId, RefreshToken> byId = new LinkedHashMap<>();

    @Override
    public Optional<RefreshToken> findByHash(TokenHash hash) {
        return byId.values().stream()
                .filter(token -> token.tokenHash().equals(hash))
                .findFirst();
    }

    @Override
    public Optional<RefreshToken> findById(RefreshTokenId id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public List<RefreshToken> findByFamily(TokenFamilyId familyId) {
        return byId.values().stream()
                .filter(token -> token.familyId().equals(familyId))
                .toList();
    }

    @Override
    public List<RefreshToken> findActiveByUser(UserId userId) {
        return byId.values().stream()
                .filter(token -> token.userId().equals(userId))
                .filter(token -> token.revokedAt() == null)
                .toList();
    }

    /**
     * 過期 token 清理(08 §8.7)。以 {@code reconstitute} 重建而非呼叫 {@code revoke()}
     * ——後者是 package-private 的聚合內部行為,本 fake 不在該套件內。
     * 述詞與正式實作一致:已過期且**尚未撤銷**。
     */
    @Override
    public int revokeExpired(Instant now, RevokedReason reason, int batchSize) {
        List<RefreshToken> victims = byId.values().stream()
                .filter(token -> !token.expiresAt().isAfter(now))
                .filter(token -> token.revokedAt() == null)
                .sorted((left, right) -> left.expiresAt().compareTo(right.expiresAt()))
                .limit(batchSize)
                .toList();
        victims.forEach(token -> byId.put(token.id(), revoked(token, now, reason)));
        return victims.size();
    }

    private static RefreshToken revoked(RefreshToken token, Instant now, RevokedReason reason) {
        return RefreshToken.reconstitute(new RefreshTokenSnapshot(
                token.id(),
                token.userId(),
                token.tokenHash(),
                token.familyId(),
                token.parentId(),
                token.issuedAt(),
                token.expiresAt(),
                token.usedAt(),
                now,
                reason,
                token.userAgent(),
                token.ip()));
    }

    @Override
    public int deleteByUser(UserId userId) {
        List<RefreshTokenId> victims = byId.values().stream()
                .filter(token -> token.userId().equals(userId))
                .map(RefreshToken::id)
                .toList();
        victims.forEach(byId::remove);
        return victims.size();
    }

    @Override
    public void saveAll(List<RefreshToken> tokens) {
        tokens.forEach(this::save);
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        byId.put(token.id(), token);
        return token;
    }
}
