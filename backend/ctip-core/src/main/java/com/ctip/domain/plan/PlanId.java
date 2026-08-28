package com.ctip.domain.plan;

import java.util.Objects;
import java.util.UUID;

/** Plan 識別碼。 */
public record PlanId(UUID value) {

    public PlanId {
        Objects.requireNonNull(value, "value 不得為 null");
    }
}
