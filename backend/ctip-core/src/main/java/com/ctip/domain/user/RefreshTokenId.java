package com.ctip.domain.user;

import java.util.Objects;
import java.util.UUID;

/** Refresh token 識別碼(User 聚合的內部實體)。 */
public record RefreshTokenId(UUID value) {

    public RefreshTokenId {
        Objects.requireNonNull(value, "value 不得為 null");
    }
}
