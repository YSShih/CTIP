package com.ctip.application.stix;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.port.StixObjectPort;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.stix.StixProjection;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.Tlp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** 投影寫出的失敗隔離(§7.8.6):單筆失敗只記錄,其餘照寫,不丟例外。 */
@Tag("unit")
class StixProjectionWriterTest {

    @Test
    void singleFailureDoesNotStopRemainingWrites() {
        List<String> written = new ArrayList<>();
        StixObjectPort port = new StixObjectPort() {
            @Override
            public Optional<Instant> findCreated(String stixId) {
                return Optional.empty();
            }

            @Override
            public void upsert(StixProjection projection) {
                if (projection.stixId().endsWith("1")) {
                    throw new IllegalStateException("boom");
                }
                written.add(projection.stixId());
            }

            @Override
            public Optional<String> findContent(String stixId) {
                return Optional.empty();
            }

            @Override
            public Map<String, String> findContents(Collection<String> stixIds) {
                return Map.of();
            }
        };

        new StixProjectionWriter(port).writeAll(List.of(projection(1), projection(2), projection(3)));

        assertThat(written)
                .containsExactly(
                        "indicator--00000000-0000-0000-0000-000000000002",
                        "indicator--00000000-0000-0000-0000-000000000003");
    }

    private static StixProjection projection(long seq) {
        UUID id = new UUID(0, seq);
        return new StixProjection(
                "indicator--" + id,
                "indicator",
                TenantId.PUBLIC,
                new IndicatorId(id),
                Tlp.CLEAR,
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-02T00:00:00Z"),
                Map.of("type", "indicator"));
    }
}
