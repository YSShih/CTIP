package com.ctip.application.indicator;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.domain.event.DomainEvent;
import com.ctip.domain.event.IndicatorEvents.IndicatorExpired;
import com.ctip.domain.fingerprint.Sha256FingerprintStrategy;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.indicator.IndicatorStatus;
import com.ctip.domain.indicator.NewIndicatorCommand;
import com.ctip.domain.source.Reputation;
import com.ctip.domain.tenant.TenantId;
import com.ctip.testing.FixedClockPort;
import com.ctip.testing.InMemoryIndicatorRepository;
import com.ctip.testing.IndicatorTestBuilder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** IOC 過期標記(docs/spec/07-domain-intel.md §7.10):validUntil 已過的 ACTIVE → EXPIRED + 事件。 */
@Tag("unit")
class IndicatorExpiryServiceTest {

    private final InMemoryIndicatorRepository indicators = new InMemoryIndicatorRepository();
    private final List<DomainEvent> published = new ArrayList<>();
    private final FixedClockPort clock = FixedClockPort.at(FixedClockPort.DEFAULT_NOW);
    private final IndicatorExpiryService service = new IndicatorExpiryService(indicators, published::add, clock);

    @Test
    void marksOnlyIndicatorsPastValidUntilAndPublishesEvents() {
        Indicator expired = indicatorWithValidUntil("expired.example.com", Duration.ofDays(-1));
        Indicator stillValid = indicatorWithValidUntil("valid.example.com", Duration.ofDays(30));
        expired.pullEvents();
        stillValid.pullEvents();

        int marked = service.markExpiredIndicators();

        assertThat(marked).isEqualTo(1);
        assertThat(expired.status()).isEqualTo(IndicatorStatus.EXPIRED);
        assertThat(stillValid.status()).isEqualTo(IndicatorStatus.ACTIVE);
        assertThat(published).hasSize(1).first().isInstanceOf(IndicatorExpired.class);
        assertThat(service.markExpiredIndicators()).isZero(); // 冪等:已 EXPIRED 不再撈出
    }

    private Indicator indicatorWithValidUntil(String domain, Duration fromNow) {
        Indicator indicator = Indicator.create(
                new NewIndicatorCommand(
                        new IndicatorId(
                                UUID.nameUUIDFromBytes(domain.getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                        TenantId.PUBLIC,
                        IndicatorTestBuilder.domainValue(domain),
                        IndicatorTestBuilder.report(IndicatorTestBuilder.SOURCE_A)
                                .validUntil(FixedClockPort.DEFAULT_NOW.plus(fromNow))
                                .build(),
                        new Reputation(70)),
                new Sha256FingerprintStrategy());
        return indicators.save(indicator);
    }
}
