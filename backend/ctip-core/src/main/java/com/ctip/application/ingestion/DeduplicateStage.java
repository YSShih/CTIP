package com.ctip.application.ingestion;

import com.ctip.application.port.IndicatorRepository;

/**
 * Stage 5 Deduplicate:批次內重複(第二次起)拒絕;
 * 否則依識別鍵 (type, normalized, ownerTenant) 查既有 Indicator(§7.4——指紋不是識別鍵)。
 */
public final class DeduplicateStage implements IngestionStage {

    private final IndicatorRepository indicators;

    public DeduplicateStage(IndicatorRepository indicators) {
        this.indicators = indicators;
    }

    @Override
    public String name() {
        return "Deduplicate";
    }

    @Override
    public IngestionContext execute(IngestionContext context) {
        String identityKey = context.type().name() + "|" + context.normalizedValue();
        if (!context.batch().markSeen(identityKey)) {
            context.reject(RejectionReason.DUPLICATE_IN_BATCH, context.normalizedValue());
            return context;
        }
        indicators
                .findByIdentity(
                        context.type(),
                        context.normalizedValue(),
                        context.source().ownerTenantId())
                .ifPresent(context::indicator);
        return context;
    }
}
