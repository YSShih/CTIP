package com.ctip.application.ingestion;

import java.util.Objects;
import java.util.UUID;

/** 匯入 job 識別碼;即對外的 {@code importJobId}。 */
public record ImportJobId(UUID value) {

    public ImportJobId {
        Objects.requireNonNull(value, "value 不得為 null");
    }
}
