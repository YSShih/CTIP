package com.ctip.application.ingestion;

import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.SourceRepository;
import com.ctip.domain.fingerprint.FingerprintStrategy;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.indicator.IndicatorSource;
import com.ctip.domain.indicator.IndicatorSourceSnapshot;
import com.ctip.domain.indicator.NewIndicatorCommand;
import com.ctip.domain.indicator.SourceRecordStatus;
import com.ctip.domain.source.Reputation;
import com.ctip.domain.source.Source;
import com.ctip.domain.source.SourceId;
import com.ctip.sdk.Confidence;
import java.util.HashMap;
import java.util.Map;

/**
 * Stage 6 Merge:既有 → {@code Indicator.mergeFrom}(同來源 UPSERT、跨來源新增,§7.5);
 * 不存在 → 建立新 Indicator。defaultTlp 與 redistributionPolicy 由來源快照(§7.9 規則 1)。
 */
public final class MergeStage implements IngestionStage {

    private final IdGeneratorPort idGenerator;
    private final FingerprintStrategy fingerprintStrategy;
    private final SourceRepository sources;

    public MergeStage(IdGeneratorPort idGenerator, FingerprintStrategy fingerprintStrategy, SourceRepository sources) {
        this.idGenerator = idGenerator;
        this.fingerprintStrategy = fingerprintStrategy;
        this.sources = sources;
    }

    @Override
    public String name() {
        return "Merge";
    }

    @Override
    public IngestionContext execute(IngestionContext context) {
        IndicatorSourceSnapshot report = reportOf(context);
        if (context.indicator() != null) {
            context.indicator()
                    .mergeFrom(new IndicatorSource(report), context.source().reputation(), knownReputations(context));
            context.merged(true);
        } else {
            NewIndicatorCommand command = new NewIndicatorCommand(
                    new IndicatorId(idGenerator.nextId()),
                    context.source().ownerTenantId(),
                    context.iocValue(),
                    report,
                    context.source().reputation());
            context.indicator(Indicator.create(command, fingerprintStrategy));
        }
        context.batch().consumeQuota();
        return context;
    }

    /** 重建後聚合的 reputations 為空;補查既有來源記錄的信譽(§7.5;缺席者以中性值 50 計)。 */
    private Map<SourceId, Reputation> knownReputations(IngestionContext context) {
        Map<SourceId, Reputation> known = new HashMap<>();
        for (var record : context.indicator().snapshot().sources()) {
            if (!record.sourceId().equals(context.source().sourceId())) {
                sources.findById(record.sourceId())
                        .map(Source::reputation)
                        .ifPresent(reputation -> known.put(record.sourceId(), reputation));
            }
        }
        return known;
    }

    private static IndicatorSourceSnapshot reportOf(IngestionContext context) {
        return new IndicatorSourceSnapshot(
                context.source().sourceId(),
                context.raw().rawValue(),
                context.raw().sourceConfidence() == null
                        ? null
                        : Confidence.of(context.raw().sourceConfidence()),
                context.raw().sourceSeverity(),
                context.source().defaultTlp(),
                context.raw().observedAt(),
                context.raw().observedAt(),
                context.raw().validUntil(),
                context.source().redistributionPolicy(),
                1,
                context.retracted() ? SourceRecordStatus.RETRACTED : SourceRecordStatus.ACTIVE,
                context.raw().tags());
    }
}
