package com.ctip.domain.user;

import java.util.Objects;
import java.util.UUID;

/** User 識別碼(docs/spec/02-ddd-model.md §2.2)。 */
public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "value 不得為 null");
    }
}
