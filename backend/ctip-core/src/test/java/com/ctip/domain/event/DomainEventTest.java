package com.ctip.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.domain.event.IngestionEvents.IngestionCompleted;
import com.ctip.domain.event.IngestionEvents.IngestionFailed;
import com.ctip.domain.event.IngestionEvents.IngestionStarted;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.tenant.TenantId;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** 事件型別與平台範圍事件的 tenant 歸屬(docs/spec/02-ddd-model.md §2.4)。 */
@Tag("unit")
class DomainEventTest {

    private static final SourceId SOURCE = new SourceId(new UUID(0, 9));

    @Test
    void eventTypeDefaultsToSimpleClassName() {
        assertThat(new IngestionStarted(SOURCE).eventType()).isEqualTo("IngestionStarted");
        assertThat(new IngestionCompleted(SOURCE, 10, 8, 2, 1).eventType()).isEqualTo("IngestionCompleted");
        assertThat(new IngestionFailed(SOURCE, "boom").eventType()).isEqualTo("IngestionFailed");
    }

    @Test
    void platformScopedEventsBelongToPublicTenant() {
        assertThat(new IngestionStarted(SOURCE).tenantId()).isEqualTo(TenantId.PUBLIC);
        assertThat(new IngestionCompleted(SOURCE, 10, 8, 2, 1).tenantId()).isEqualTo(TenantId.PUBLIC);
        assertThat(new IngestionFailed(SOURCE, "boom").tenantId()).isEqualTo(TenantId.PUBLIC);
        assertThat(new SourceEvents.SourceDegraded(SOURCE, 3).tenantId()).isEqualTo(TenantId.PUBLIC);
        assertThat(new SourceEvents.SourceFailed(SOURCE, 10).tenantId()).isEqualTo(TenantId.PUBLIC);
        assertThat(new SourceEvents.SourceRecovered(SOURCE).tenantId()).isEqualTo(TenantId.PUBLIC);
    }

    @Test
    void pendingEventsPullClearsTheBuffer() {
        PendingEvents pending = new PendingEvents();
        pending.record(new IngestionStarted(SOURCE));
        assertThat(pending.pull()).hasSize(1);
        assertThat(pending.pull()).isEmpty();
    }
}
