package com.ctip.infrastructure.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.adapters.mock.MockAbuseIPDBAdapter;
import com.ctip.adapters.mock.MockOpenPhishAdapter;
import com.ctip.sdk.SourceType;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** docs/spec/08-ingestion-sdk.md §8.1:同一 SourceType 重複註冊必須於啟動時失敗,不得後者覆蓋前者。 */
@Tag("unit")
class AdapterRegistryTest {

    @Test
    void findsRegisteredAdapterBySourceType() {
        AdapterRegistry registry = new AdapterRegistry(List.of(new MockOpenPhishAdapter(), new MockAbuseIPDBAdapter()));
        assertThat(registry.find(SourceType.MOCK_OPENPHISH)).containsInstanceOf(MockOpenPhishAdapter.class);
        assertThat(registry.find(SourceType.MOCK_ALIENVAULT)).isEmpty();
    }

    @Test
    void duplicateSourceTypeFailsAtConstruction() {
        List<com.ctip.sdk.ThreatSourceAdapter> duplicated =
                List.of(new MockOpenPhishAdapter(), new MockOpenPhishAdapter());
        assertThatThrownBy(() -> new AdapterRegistry(duplicated)).isInstanceOf(IllegalStateException.class);
    }
}
