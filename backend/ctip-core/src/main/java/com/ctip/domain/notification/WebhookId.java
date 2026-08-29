package com.ctip.domain.notification;

import java.util.Objects;
import java.util.UUID;

/** Webhook 識別碼(docs/spec/03-diagrams.md §3.2.9)。 */
public record WebhookId(UUID value) {

    public WebhookId {
        Objects.requireNonNull(value, "value 不得為 null");
    }
}
