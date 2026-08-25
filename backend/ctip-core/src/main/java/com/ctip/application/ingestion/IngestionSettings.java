package com.ctip.application.ingestion;

/** ingestion 組態(來自 ctip.ingestion.*;INGESTION_BATCH_SIZE 預設 500,一批一交易)。 */
public record IngestionSettings(boolean enabled, int batchSize) {

    public IngestionSettings {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize 必須為正數:" + batchSize);
        }
    }
}
