package com.ctip.application.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.domain.source.Reputation;
import com.ctip.domain.source.Source;
import com.ctip.domain.source.SourceHealth;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.source.SourceSnapshot;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.SourceType;
import com.ctip.sdk.Tlp;
import com.ctip.testing.InMemorySourceRepository;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** {@code PATCH /admin/sources/{id}}:enabled 是來源上唯一由外部意志決定的欄位。 */
@Tag("unit")
class SourceAdminServiceTest {

    private static final SourceId SOURCE_ID = new SourceId(UUID.fromString("00000000-0000-0000-0000-0000000000bb"));

    @Test
    void disablingAndEnablingASourceIsPersisted() {
        InMemorySourceRepository sources = new InMemorySourceRepository();
        sources.enabledSyncable(List.of(syncableSource()));
        SourceAdminService service = new SourceAdminService(sources, null);

        assertThat(service.setEnabled(SOURCE_ID, false).enabled()).isFalse();
        assertThat(service.setEnabled(SOURCE_ID, true).enabled()).isTrue();
        assertThat(sources.saved()).hasSize(2);
    }

    @Test
    void anUnknownSourceIsReportedAsNotFound() {
        SourceAdminService service = new SourceAdminService(new InMemorySourceRepository(), null);

        assertThatThrownBy(() -> service.setEnabled(new SourceId(UUID.randomUUID()), false))
                .isInstanceOf(AdminResourceNotFoundException.class);
    }

    private static Source syncableSource() {
        return Source.reconstitute(new SourceSnapshot(
                SOURCE_ID,
                SourceType.MOCK_OPENPHISH,
                "Mock OpenPhish",
                "https://openphish.example.org",
                Tlp.CLEAR,
                RedistributionPolicy.ATTRIBUTION_REQUIRED,
                new Reputation(70),
                true,
                true,
                Duration.ofHours(1),
                SourceHealth.initial(),
                null,
                null,
                0));
    }
}
