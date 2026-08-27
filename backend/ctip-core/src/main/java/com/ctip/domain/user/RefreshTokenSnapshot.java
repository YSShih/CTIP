package com.ctip.domain.user;

import java.time.Instant;

/**
 * RefreshToken 的持久化狀態載體(欄位數超過建構子參數上限,依既有 SourceSnapshot 慣例以 record 承載)。
 * 對應 docs/spec/04-data-dictionary.md 表 15。
 */
public record RefreshTokenSnapshot(
        RefreshTokenId id,
        UserId userId,
        TokenHash tokenHash,
        TokenFamilyId familyId,
        RefreshTokenId parentId,
        Instant issuedAt,
        Instant expiresAt,
        Instant usedAt,
        Instant revokedAt,
        RevokedReason revokedReason,
        String userAgent,
        String ip) {}
