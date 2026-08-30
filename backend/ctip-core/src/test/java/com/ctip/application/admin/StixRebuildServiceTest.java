package com.ctip.application.admin;

import static com.ctip.testing.IndicatorTestBuilder.SOURCE_A;
import static com.ctip.testing.IndicatorTestBuilder.domainValue;
import static com.ctip.testing.IndicatorTestBuilder.report;
import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.stix.StixProjectionFactory;
import com.ctip.application.stix.StixProjectionWriter;
import com.ctip.domain.fingerprint.Sha256FingerprintStrategy;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.indicator.NewIndicatorCommand;
import com.ctip.domain.source.Reputation;
import com.ctip.domain.tenant.TenantId;
import com.ctip.testing.FixedClockPort;
import com.ctip.testing.InMemoryIndicatorRepository;
import com.ctip.testing.InMemorySourceRepository;
import com.ctip.testing.InMemoryStixObjects;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@code POST /admin/stix/rebuild}:由 domain 重算全部投影(§7.8.6 的衍生資料)。
 * 重點是<strong>逐批掃描會走完全部</strong>而不是只處理第一批。
 */
@Tag("unit")
class StixRebuildServiceTest {

    @Test
    void everyIndicatorIsReprojectedAcrossBatches() {
        InMemoryIndicatorRepository indicators = new InMemoryIndicatorRepository();
        for (int i = 0; i < 5; i++) {
            indicators.save(indicator(i));
        }
        InMemoryStixObjects stixObjects = new InMemoryStixObjects();
        StixRebuildService service = new StixRebuildService(
                indicators,
                new StixProjectionFactory(
                        new InMemorySourceRepository(), stixObjects, FixedClockPort.at(FixedClockPort.DEFAULT_NOW)),
                new StixProjectionWriter(stixObjects));

        assertThat(service.rebuildAll()).isEqualTo(5);
    }

    private static Indicator indicator(int index) {
        String value = "rebuild-" + index + ".ctip-sample.net";
        return Indicator.create(
                new NewIndicatorCommand(
                        new IndicatorId(java.util.UUID.nameUUIDFromBytes(
                                value.getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                        TenantId.PUBLIC,
                        domainValue(value),
                        report(SOURCE_A).build(),
                        new Reputation(70)),
                new Sha256FingerprintStrategy());
    }

    @Test
    void anEmptyDatabaseRebuildsNothing() {
        InMemoryStixObjects stixObjects = new InMemoryStixObjects();
        StixRebuildService service = new StixRebuildService(
                new InMemoryIndicatorRepository(),
                new StixProjectionFactory(
                        new InMemorySourceRepository(), stixObjects, FixedClockPort.at(FixedClockPort.DEFAULT_NOW)),
                new StixProjectionWriter(stixObjects));

        assertThat(service.rebuildAll()).isZero();
    }
}
