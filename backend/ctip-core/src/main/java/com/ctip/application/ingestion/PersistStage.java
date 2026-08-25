package com.ctip.application.ingestion;

import com.ctip.application.port.IndicatorRepository;

/** Stage 9 Persist:PostgreSQL 為 source of truth(§8.2);交易邊界在批次處理器(一批一交易)。 */
public final class PersistStage implements IngestionStage {

    private final IndicatorRepository indicators;

    public PersistStage(IndicatorRepository indicators) {
        this.indicators = indicators;
    }

    @Override
    public String name() {
        return "Persist";
    }

    @Override
    public IngestionContext execute(IngestionContext context) {
        indicators.save(context.indicator());
        return context;
    }
}
