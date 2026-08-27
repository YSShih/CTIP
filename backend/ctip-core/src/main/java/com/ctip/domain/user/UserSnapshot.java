package com.ctip.domain.user;

import com.ctip.domain.tenant.TenantId;
import java.time.Instant;

/** User 的持久化狀態載體(docs/spec/04-data-dictionary.md 表 10)。 */
public record UserSnapshot(
        UserId id,
        EmailAddress email,
        PasswordHash passwordHash,
        String displayName,
        UserStatus status,
        TenantId primaryTenantId,
        Instant lastLoginAt,
        int failedLoginCount,
        Instant lockedUntil) {}
