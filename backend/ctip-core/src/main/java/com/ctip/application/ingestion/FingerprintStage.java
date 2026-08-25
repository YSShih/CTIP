package com.ctip.application.ingestion;

import com.ctip.domain.fingerprint.FingerprintStrategy;

/** Stage 4 Fingerprint:對 normalizedValue 計算 SHA-256(§7.4;絕不對原始值計算)。 */
public final class FingerprintStage implements IngestionStage {

    private final FingerprintStrategy strategy;

    public FingerprintStage(FingerprintStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public String name() {
        return "Fingerprint";
    }

    @Override
    public IngestionContext execute(IngestionContext context) {
        context.fingerprint(strategy.fingerprint(context.normalizedValue()));
        return context;
    }
}
