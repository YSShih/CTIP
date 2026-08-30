package com.ctip.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.observability.CtipMetricNames;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.SourceRepository;
import com.ctip.domain.source.Reputation;
import com.ctip.domain.source.Source;
import com.ctip.domain.source.SourceHealth;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.source.SourceSnapshot;
import com.ctip.domain.source.SourceStatus;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.SourceType;
import com.ctip.sdk.Tlp;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** {@code ctip.source.sync.lag{source}}(docs/spec/13-platform-ops.md §13.6)。 */
@Tag("unit")
class SourceSyncLagBinderTest {

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");
    private static final ClockPort CLOCK = () -> NOW;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void theLagIsTheTimeSinceTheLastSuccessfulSync() {
        bind(source(NOW.minus(Duration.ofMinutes(30))));

        assertThat(registry.get(CtipMetricNames.SOURCE_SYNC_LAG)
                        .tag("source", SourceType.MOCK_OPENPHISH.name())
                        .gauge()
                        .value())
                .isEqualTo(1_800);
    }

    /** 從未成功過的來源不是「剛剛同步過」——回 NaN,不回 0。 */
    @Test
    void aSourceThatNeverSucceededReportsNaN() {
        bind(source(null));

        assertThat(registry.get(CtipMetricNames.SOURCE_SYNC_LAG).gauge().value())
                .isNaN();
    }

    /** 資料庫暫時不可用不得讓指標蒐集炸掉。 */
    @Test
    void aFailingRepositoryDoesNotPropagate() {
        SourceSyncLagBinder binder = new SourceSyncLagBinder(failingRepository(), CLOCK);

        binder.bindTo(registry);

        assertThat(registry.find(CtipMetricNames.SOURCE_SYNC_LAG).gauges()).isEmpty();
    }

    private void bind(Source source) {
        new SourceSyncLagBinder(repositoryOf(source), CLOCK).bindTo(registry);
    }

    private static Source source(Instant lastSuccessAt) {
        return Source.reconstitute(new SourceSnapshot(
                new SourceId(java.util.UUID.fromString("00000000-0000-0000-0000-0000000000a1")),
                SourceType.MOCK_OPENPHISH,
                "Mock OpenPhish",
                null,
                Tlp.CLEAR,
                RedistributionPolicy.PUBLIC_REDISTRIBUTABLE,
                new Reputation(70),
                true,
                true,
                Duration.ofHours(1),
                new SourceHealth(SourceStatus.ACTIVE, 0, lastSuccessAt, lastSuccessAt, null, null),
                null,
                null,
                0L));
    }

    private static SourceRepository repositoryOf(Source source) {
        return new StubRepository(List.of(source));
    }

    private static SourceRepository failingRepository() {
        return new StubRepository(null);
    }

    private record StubRepository(List<Source> sources) implements SourceRepository {

        @Override
        public Optional<Source> findById(SourceId id) {
            return Optional.empty();
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
        public List<Source> findAll() {
            if (sources == null) {
                throw new IllegalStateException("資料庫不可用(測試)");
            }
            return sources;
        }

        @Override
        public Source save(Source source) {
            return source;
        }
    }
}
