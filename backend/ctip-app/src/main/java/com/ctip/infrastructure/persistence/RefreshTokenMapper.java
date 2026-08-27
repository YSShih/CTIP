package com.ctip.infrastructure.persistence;

import com.ctip.domain.user.RefreshToken;
import com.ctip.domain.user.RefreshTokenId;
import com.ctip.domain.user.RefreshTokenSnapshot;
import com.ctip.domain.user.RevokedReason;
import com.ctip.domain.user.TokenFamilyId;
import com.ctip.domain.user.TokenHash;
import com.ctip.domain.user.UserId;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

/** RefreshToken(User 聚合的內部實體)↔ JPA entity。 */
@Mapper(componentModel = "spring")
interface RefreshTokenMapper {

    default RefreshToken toDomain(RefreshTokenEntity e) {
        return RefreshToken.reconstitute(new RefreshTokenSnapshot(
                new RefreshTokenId(e.id),
                new UserId(e.userId),
                new TokenHash(e.tokenHash.trim()),
                new TokenFamilyId(e.familyId),
                e.parentId == null ? null : new RefreshTokenId(e.parentId),
                e.issuedAt,
                e.expiresAt,
                e.usedAt,
                e.revokedAt,
                e.revokedReason == null ? null : RevokedReason.valueOf(e.revokedReason),
                e.userAgent,
                e.ip));
    }

    default void updateEntity(RefreshToken token, @MappingTarget RefreshTokenEntity e) {
        e.id = token.id().value();
        e.userId = token.userId().value();
        e.tokenHash = token.tokenHash().value();
        e.familyId = token.familyId().value();
        e.parentId = token.parentId() == null ? null : token.parentId().value();
        e.issuedAt = token.issuedAt();
        e.expiresAt = token.expiresAt();
        e.usedAt = token.usedAt();
        e.revokedAt = token.revokedAt();
        e.revokedReason =
                token.revokedReason() == null ? null : token.revokedReason().name();
        e.userAgent = token.userAgent();
        e.ip = token.ip();
    }
}
