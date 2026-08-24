package com.ctip.domain.source;

import java.util.Objects;
import java.util.UUID;

/** Source 識別碼。 */
public record SourceId(UUID value) {

    public SourceId {
        Objects.requireNonNull(value, "value 不得為 null");
    }
}
