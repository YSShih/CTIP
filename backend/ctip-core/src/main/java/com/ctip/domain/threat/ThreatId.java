package com.ctip.domain.threat;

import java.util.Objects;
import java.util.UUID;

/** Threat 聚合根識別碼(docs/spec/02-ddd-model.md §2.6)。 */
public record ThreatId(UUID value) {

    public ThreatId {
        Objects.requireNonNull(value, "value 不得為 null");
    }
}
