package com.ctip.domain.indicator;

import java.util.Objects;
import java.util.UUID;

/** Indicator 識別碼。 */
public record IndicatorId(UUID value) {

    public IndicatorId {
        Objects.requireNonNull(value, "value 不得為 null");
    }
}
