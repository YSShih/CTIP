package com.ctip.infrastructure.persistence;

import com.ctip.domain.identity.ApiKey;
import com.ctip.domain.identity.ApiKeyId;
import com.ctip.domain.identity.ApiKeySnapshot;
import com.ctip.domain.identity.KeyHash;
import com.ctip.domain.identity.KeyPrefix;
import com.ctip.domain.identity.ScopeSet;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.UserId;
import java.util.LinkedHashSet;
import java.util.Set;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

/** ApiKey domain ↔ JPA entity。CHAR 欄位讀回時可能帶尾空白,一律 trim。 */
@Mapper(componentModel = "spring")
interface ApiKeyMapper {

    default ApiKey toDomain(ApiKeyEntity e) {
        return ApiKey.reconstitute(new ApiKeySnapshot(
                new ApiKeyId(e.id),
                new TenantId(e.tenantId),
                new UserId(e.userId),
                e.name,
                new KeyPrefix(e.keyPrefix.trim()),
                new KeyHash(e.keyHash.trim()),
                new ScopeSet(new LinkedHashSet<>(Set.of(e.scopes))),
                e.expiresAt,
                e.lastUsedAt,
                e.revokedAt,
                e.createdAt));
    }

    default void updateEntity(ApiKey apiKey, @MappingTarget ApiKeyEntity e) {
        e.id = apiKey.id().value();
        e.tenantId = apiKey.tenantId().value();
        e.userId = apiKey.userId().value();
        e.name = apiKey.name();
        e.keyPrefix = apiKey.keyPrefix().value();
        e.keyHash = apiKey.keyHash().value();
        e.scopes = apiKey.scopes().values().toArray(String[]::new);
        e.expiresAt = apiKey.expiresAt();
        e.lastUsedAt = apiKey.lastUsedAt();
        e.revokedAt = apiKey.revokedAt();
        e.createdAt = apiKey.createdAt();
    }
}
