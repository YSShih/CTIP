package com.ctip.domain.indicator;

import java.time.Instant;
import java.util.Objects;

/** 有效期值物件:until 為 null(永不過期)或晚於 from(docs/spec/02-ddd-model.md §2.6)。 */
public record ValidityPeriod(Instant from, Instant until) {

    public ValidityPeriod {
        Objects.requireNonNull(from, "from 不得為 null");
        if (until != null && !until.isAfter(from)) {
            throw new IllegalArgumentException("until 必須為 null 或晚於 from");
        }
    }

    public boolean isExpiredAt(Instant now) {
        return until != null && until.isBefore(now);
    }
}
