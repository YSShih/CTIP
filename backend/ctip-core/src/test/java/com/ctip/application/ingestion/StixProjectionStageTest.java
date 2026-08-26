package com.ctip.application.ingestion;

import static com.ctip.testing.IndicatorTestBuilder.SOURCE_A;
import static com.ctip.testing.IndicatorTestBuilder.SOURCE_B;
import static com.ctip.testing.IndicatorTestBuilder.T0;
import static com.ctip.testing.IndicatorTestBuilder.report;
import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.port.StixObjectPort;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorSource;
import com.ctip.domain.source.Reputation;
import com.ctip.domain.source.Source;
import com.ctip.domain.source.SourceHealth;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.source.SourceSnapshot;
import com.ctip.domain.stix.StixProjection;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.RawThreatRecord;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.SourceType;
import com.ctip.sdk.Tlp;
import com.ctip.testing.IndicatorTestBuilder;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Stage 8(§8.2、§7.8.2):投影建構、created 沿用既有投影、
 * external_references 僅含可標註政策的來源、任何錯誤不 reject(§7.8.6)。
 */
@Tag("unit")
class StixProjectionStageTest {

    private static final Instant NOW = T0.plus(Duration.ofDays(3));

    @Test
    void buildsProjectionWithMappedContent() {
        Indicator indicator = IndicatorTestBuilder.activeIndicator(
                TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.ATTRIBUTION_REQUIRED);
        indicator.mergeFrom(
                new IndicatorSource(report(SOURCE_B)
                        .policy(RedistributionPolicy.INTERNAL_ONLY)
                        .build()),
                new Reputation(60));
        IngestionContext context = contextWith(indicator);

        new StixProjectionStage(sourcesWithNames(), stixObjects(Optional.empty()), () -> NOW).execute(context);

        StixProjection projection = context.stixProjection();
        assertThat(projection).isNotNull();
        assertThat(projection.stixId()).isEqualTo("indicator--" + indicator.id().value());
        assertThat(projection.tlp()).isEqualTo(Tlp.CLEAR);
        assertThat(projection.created()).isEqualTo(NOW); // 無既有投影 → created = now
        assertThat(projection.modified()).isEqualTo(NOW);
        Map<String, Object> content = projection.content();
        assertThat(content.get("pattern")).isEqualTo("[domain-name:value = 'mal-example.ctip-sample.net']");
        assertThat(content.get("valid_from")).isEqualTo("2026-08-01T00:00:00.000Z");
        assertThat(content.get("valid_until")).isEqualTo("2026-10-30T00:00:00.000Z"); // T0 + 90 天(DOMAIN TTL)
        assertThat(content.get("indicator_types")).isEqualTo(List.of("malicious-activity"));
        assertThat(content.get("labels")).isEqualTo(List.of("severity:MEDIUM", "score:" + indicator.score()));
        assertThat(content.get("object_marking_refs"))
                .isEqualTo(List.of("marking-definition--94868c89-83c2-464b-929b-a1a8aa3c8487"));
        // INTERNAL_ONLY 來源不得出現在 external_references(§7.8.2、§7.9)
        assertThat(content.get("external_references"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .hasSize(1);
        assertThat(content).doesNotContainKey("revoked");
    }

    @Test
    void createdIsPreservedFromExistingProjection() {
        Indicator indicator = IndicatorTestBuilder.activeIndicator(
                TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        IngestionContext context = contextWith(indicator);
        Instant existingCreated = T0.minus(Duration.ofDays(10));

        new StixProjectionStage(sourcesWithNames(), stixObjects(Optional.of(existingCreated)), () -> NOW)
                .execute(context);

        assertThat(context.stixProjection().created()).isEqualTo(existingCreated);
        assertThat(context.stixProjection().modified()).isEqualTo(NOW);
    }

    @Test
    void projectionFailureDoesNotRejectRecord() {
        Indicator indicator = IndicatorTestBuilder.activeIndicator(
                TenantId.PUBLIC, Tlp.CLEAR, RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        IngestionContext context = contextWith(indicator);
        StixObjectPort failing = new StubStixObjects(Optional.empty()) {
            @Override
            public Optional<Instant> findCreated(String stixId) {
                throw new IllegalStateException("boom");
            }
        };

        new StixProjectionStage(sourcesWithNames(), failing, () -> NOW).execute(context);

        assertThat(context.rejected()).isFalse();
        assertThat(context.stixProjection()).isNull();
    }

    private static IngestionContext contextWith(Indicator indicator) {
        RawThreatRecord raw = new RawThreatRecord(
                "mal-example.ctip-sample.net", null, null, T0, null, null, null, java.util.Set.of(), Map.of());
        SourceContext source = new SourceContext(
                SOURCE_A,
                TenantId.PUBLIC,
                Tlp.CLEAR,
                RedistributionPolicy.PUBLIC_REDISTRIBUTABLE,
                new Reputation(70),
                false);
        IngestionContext context = new IngestionContext(raw, source, new BatchState(UUID.randomUUID(), null));
        context.indicator(indicator);
        return context;
    }

    private static com.ctip.application.port.SourceRepository sourcesWithNames() {
        return new com.ctip.application.port.SourceRepository() {
            @Override
            public Optional<Source> findById(SourceId id) {
                return Optional.of(Source.reconstitute(new SourceSnapshot(
                        id,
                        SourceType.MOCK_OPENPHISH,
                        "Mock Source " + id.value(),
                        Tlp.CLEAR,
                        RedistributionPolicy.PUBLIC_REDISTRIBUTABLE,
                        new Reputation(70),
                        true,
                        true,
                        Duration.ofHours(1),
                        SourceHealth.initial(),
                        null,
                        null,
                        0)));
            }

            @Override
            public Optional<Source> findBySourceType(SourceType sourceType) {
                return Optional.empty();
            }

            @Override
            public List<Source> findEnabledSyncable() {
                return List.of();
            }

            @Override
            public Source save(Source source) {
                return source;
            }
        };
    }

    private static StixObjectPort stixObjects(Optional<Instant> created) {
        return new StubStixObjects(created);
    }

    private static class StubStixObjects implements StixObjectPort {
        private final Optional<Instant> created;

        private StubStixObjects(Optional<Instant> created) {
            this.created = created;
        }

        @Override
        public Optional<Instant> findCreated(String stixId) {
            return created;
        }

        @Override
        public void upsert(StixProjection projection) {
            // stage 不寫出(寫出在 IngestionBatchExecutor)
        }

        @Override
        public Optional<String> findContent(String stixId) {
            return Optional.empty();
        }

        @Override
        public Map<String, String> findContents(Collection<String> stixIds) {
            return Map.of();
        }
    }
}
