package com.ctip.domain.identity;

import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.UserId;
import java.time.Instant;

/** ApiKey 的持久化狀態載體(docs/spec/04-data-dictionary.md 表 16)。 */
public record ApiKeySnapshot(
        ApiKeyId id,
        TenantId tenantId,
        UserId userId,
        String name,
        KeyPrefix keyPrefix,
        KeyHash keyHash,
        ScopeSet scopes,
        Instant expiresAt,
        Instant lastUsedAt,
        Instant revokedAt,
        Instant createdAt) {}
