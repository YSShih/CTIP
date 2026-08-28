package com.ctip.domain.bloom;

import java.util.Objects;
import java.util.UUID;

/** BloomVersion 聚合根的識別碼。 */
public record BloomVersionId(UUID value) {

    public BloomVersionId {
        Objects.requireNonNull(value, "value 不得為 null");
    }
}
