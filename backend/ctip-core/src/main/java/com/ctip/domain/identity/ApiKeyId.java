package com.ctip.domain.identity;

import java.util.Objects;
import java.util.UUID;

/** ApiKey 識別碼。 */
public record ApiKeyId(UUID value) {

    public ApiKeyId {
        Objects.requireNonNull(value, "value 不得為 null");
    }
}
