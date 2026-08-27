package com.ctip.infrastructure.persistence;

import com.ctip.application.port.RefreshTokenRepository;
import com.ctip.domain.user.RefreshToken;
import com.ctip.domain.user.RefreshTokenId;
import com.ctip.domain.user.TokenFamilyId;
import com.ctip.domain.user.TokenHash;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** RefreshTokenRepository port 的 JPA 實作(ADR 0012 決策 4:認證熱路徑以雜湊定位單一枚)。 */
@Repository
@Transactional
class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository jpa;
    private final RefreshTokenMapper mapper;

    RefreshTokenRepositoryAdapter(RefreshTokenJpaRepository jpa, RefreshTokenMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByHash(TokenHash hash) {
        return jpa.findByTokenHash(hash.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findById(RefreshTokenId id) {
        return jpa.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefreshToken> findByFamily(TokenFamilyId familyId) {
        return jpa.findByFamilyId(familyId.value()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public void saveAll(List<RefreshToken> tokens) {
        tokens.forEach(this::save);
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        RefreshTokenEntity entity = jpa.findById(token.id().value()).orElseGet(RefreshTokenEntity::new);
        mapper.updateEntity(token, entity);
        return mapper.toDomain(jpa.save(entity));
    }
}
