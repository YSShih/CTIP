package com.ctip.testing;

import com.ctip.application.port.RefreshTokenRepository;
import com.ctip.domain.user.RefreshToken;
import com.ctip.domain.user.RefreshTokenId;
import com.ctip.domain.user.TokenFamilyId;
import com.ctip.domain.user.TokenHash;
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
    public void saveAll(List<RefreshToken> tokens) {
        tokens.forEach(this::save);
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        byId.put(token.id(), token);
        return token;
    }
}
