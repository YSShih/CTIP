package com.ctip.config;

import com.ctip.application.ingestion.DeduplicateStage;
import com.ctip.application.ingestion.EventPublishStage;
import com.ctip.application.ingestion.FingerprintStage;
import com.ctip.application.ingestion.IngestionPipeline;
import com.ctip.application.ingestion.IngestionSettings;
import com.ctip.application.ingestion.MergeStage;
import com.ctip.application.ingestion.NormalizeStage;
import com.ctip.application.ingestion.ParseStage;
import com.ctip.application.ingestion.PersistStage;
import com.ctip.application.ingestion.ScoreStage;
import com.ctip.application.ingestion.StixProjectionStage;
import com.ctip.application.ingestion.ValidateStage;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.EventPublisherPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.IndicatorRepository;
import com.ctip.application.port.SourceRepository;
import com.ctip.application.port.StixObjectPort;
import com.ctip.domain.fingerprint.FingerprintStrategy;
import com.ctip.domain.fingerprint.Sha256FingerprintStrategy;
import com.ctip.domain.indicator.RuleBasedThreatScorer;
import com.ctip.domain.indicator.ThreatScorer;
import com.ctip.domain.indicator.normalization.IocNormalizers;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Pipeline 裝配(docs/spec/08-ingestion-sdk.md §8.2):stage 順序以顯式 List.of 表達,
 * 不依賴 @Order,在單一方法內一眼看完。Phase 8 在 Score 之後插入 StixProjectionStage;
 * M2 在 Persist 之後插入 BloomUpdateStage 與 SearchIndexStage——只改本檔的 List.of。
 * 規格 §8.2 的 bean 簽章範例有 10 個參數,違反自身的 checkstyle ParameterNumber(≤ 5),
 * 故改為內聯建構(ADR 0004);stage 為純類別,單元測試直接 new。
 */
@Configuration(proxyBeanMethods = false)
public class IngestionPipelineConfig {

    /** pipeline 用到的 out-port 組合(參數數 ≤ 5 的組合;僅本組態使用)。 */
    record Repositories(IndicatorRepository indicators, SourceRepository sources, StixObjectPort stixObjects) {}

    @Bean
    Repositories ingestionRepositories(
            IndicatorRepository indicators, SourceRepository sources, StixObjectPort stixObjects) {
        return new Repositories(indicators, sources, stixObjects);
    }

    /** 查詢層(lookup 的推斷+正規化)共用;pipeline 因 bean 方法參數上限另行內聯建構(組態相同)。 */
    @Bean
    IocNormalizers iocNormalizers(CtipProperties properties) {
        return new IocNormalizers(properties.normalization().stripWww());
    }

    @Bean
    IngestionSettings ingestionSettings(CtipProperties properties) {
        return new IngestionSettings(
                properties.ingestion().enabled(), properties.ingestion().batchSize());
    }

    @Bean
    IngestionPipeline ingestionPipeline(
            CtipProperties properties,
            Repositories repositories,
            IdGeneratorPort idGenerator,
            EventPublisherPort events,
            ClockPort clock) {
        IocNormalizers normalizers =
                new IocNormalizers(properties.normalization().stripWww());
        FingerprintStrategy fingerprint = new Sha256FingerprintStrategy();
        ThreatScorer scorer = new RuleBasedThreatScorer(clock::now);
        return new IngestionPipeline(List.of(
                new ParseStage(normalizers),
                new ValidateStage(),
                new NormalizeStage(
                        normalizers, Set.copyOf(properties.dataQuality().domainAllowlist())),
                new FingerprintStage(fingerprint),
                new DeduplicateStage(repositories.indicators()),
                new MergeStage(idGenerator, fingerprint, repositories.sources()),
                new ScoreStage(scorer),
                // stage 8(§8.2):投影建構;寫出由 IngestionBatchExecutor 於交易提交後執行(ADR 0005)
                new StixProjectionStage(repositories.sources(), repositories.stixObjects(), clock),
                new PersistStage(repositories.indicators()),
                new EventPublishStage(events)));
    }
}
