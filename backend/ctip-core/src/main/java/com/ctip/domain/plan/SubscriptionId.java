package com.ctip.domain.plan;

import java.util.Objects;
import java.util.UUID;

/** Subscription 識別碼。 */
public record SubscriptionId(UUID value) {

    public SubscriptionId {
        Objects.requireNonNull(value, "value 不得為 null");
    }
}
