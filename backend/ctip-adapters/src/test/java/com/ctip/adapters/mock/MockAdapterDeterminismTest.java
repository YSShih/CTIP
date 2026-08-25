package com.ctip.adapters.mock;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.sdk.FetchContext;
import com.ctip.sdk.FetchResult;
import com.ctip.sdk.IocType;
import com.ctip.sdk.RawThreatRecord;
import com.ctip.sdk.ThreatSourceAdapter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 三個 mock adapter 的確定性(docs/spec/08-ingestion-sdk.md §8.3 要求 1):
 * 同一 FetchContext 連續呼叫兩次,結果 equals;另驗證要求 3 的跨來源重疊(≥ 10 個)。
 */
@Tag("unit")
class MockAdapterDeterminismTest {

    private static final FetchContext FIRST_SYNC = new FetchContext(null, null, Map.of(), 1000);

    static Stream<ThreatSourceAdapter> adapters() {
        return Stream.of(new MockOpenPhishAdapter(), new MockAbuseIPDBAdapter(), new MockAlienVaultAdapter());
    }

    @ParameterizedTest
    @MethodSource("adapters")
    void sameContextTwiceReturnsEqualResults(ThreatSourceAdapter adapter) {
        assertThat(adapter.fetch(FIRST_SYNC)).isEqualTo(adapter.fetch(FIRST_SYNC));

        FetchContext secondPage =
                new FetchContext(null, adapter.fetch(FIRST_SYNC).nextCursor(), Map.of(), 1000);
        assertThat(adapter.fetch(secondPage)).isEqualTo(adapter.fetch(secondPage));
    }

    @ParameterizedTest
    @MethodSource("adapters")
    void pagingChainIsDeterministicRegardlessOfPageSize(ThreatSourceAdapter adapter) {
        FetchContext smallPages = new FetchContext(null, null, Map.of(), 5);
        List<RawThreatRecord> smallWalk = fetchAll(adapter, smallPages);

        assertThat(smallWalk)
                .isEqualTo(fetchAll(adapter, smallPages))
                .isEqualTo(fetchAll(adapter, FIRST_SYNC))
                .hasSizeGreaterThan(10);
    }

    @ParameterizedTest
    @MethodSource("adapters")
    void lastPageTerminatesWithoutCursor(ThreatSourceAdapter adapter) {
        FetchContext context = FIRST_SYNC;
        FetchResult page = adapter.fetch(context);
        int pages = 1;
        while (page.hasMore()) {
            assertThat(page.nextCursor()).isNotNull();
            page = adapter.fetch(new FetchContext(null, page.nextCursor(), Map.of(), 1000));
            pages++;
        }
        assertThat(page.nextCursor()).isNull();
        assertThat(pages).isGreaterThan(1); // pageSize 小於資料集,分頁必然發生
    }

    @ParameterizedTest
    @MethodSource("adapters")
    void sinceFilterReturnsDeterministicSubset(ThreatSourceAdapter adapter) {
        Instant since = Instant.parse("2026-08-10T00:00:00Z");
        FetchContext incremental = new FetchContext(since, null, Map.of(), 1000);
        List<RawThreatRecord> filtered = fetchAll(adapter, incremental);

        assertThat(filtered)
                .isEqualTo(fetchAll(adapter, incremental))
                .isNotEmpty()
                .allSatisfy(r -> assertThat(r.observedAt()).isAfter(since));
        assertThat(filtered).hasSizeLessThan(fetchAll(adapter, FIRST_SYNC).size());
    }

    @ParameterizedTest
    @MethodSource("adapters")
    void metadataDeclaresOnlyEmittedTypes(ThreatSourceAdapter adapter) {
        Set<IocType> declared = adapter.metadata().supportedIocTypes();
        assertThat(fetchAll(adapter, FIRST_SYNC)).allSatisfy(r -> {
            if (r.declaredType() != null) {
                assertThat(declared).contains(r.declaredType());
            }
        });
        assertThat(adapter.metadata().requiresCredentials()).isFalse();
    }

    @Test
    void atLeastTenIocsOverlapAcrossAdapters() {
        List<String> shared = SharedIocs.all();
        assertThat(shared).hasSizeGreaterThanOrEqualTo(10);

        Set<String> openPhish = rawValues(new MockOpenPhishAdapter());
        Set<String> abuseIpdb = rawValues(new MockAbuseIPDBAdapter());
        Set<String> alienVault = rawValues(new MockAlienVaultAdapter());
        for (String value : shared) {
            long holders = Stream.of(openPhish, abuseIpdb, alienVault)
                    .filter(values -> values.contains(value))
                    .count();
            assertThat(holders).as("重疊 IOC 必須出現在至少兩個 mock:%s", value).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void alienVaultMarksExactlyOneSharedIocAsRevoked() {
        List<RawThreatRecord> revoked = fetchAll(new MockAlienVaultAdapter(), FIRST_SYNC).stream()
                .filter(r -> Boolean.TRUE.equals(r.rawPayload().get("revoked")))
                .toList();
        assertThat(revoked).hasSize(1);
        assertThat(revoked.getFirst().rawValue()).isEqualTo(SharedIocs.DOMAIN_RETRACTED_BY_ALIENVAULT);
    }

    private static List<RawThreatRecord> fetchAll(ThreatSourceAdapter adapter, FetchContext initial) {
        List<RawThreatRecord> all = new ArrayList<>();
        FetchContext context = initial;
        FetchResult page = adapter.fetch(context);
        all.addAll(page.records());
        while (page.hasMore()) {
            context = new FetchContext(initial.since(), page.nextCursor(), initial.config(), initial.maxRecords());
            page = adapter.fetch(context);
            all.addAll(page.records());
        }
        return all;
    }

    private static Set<String> rawValues(ThreatSourceAdapter adapter) {
        return new HashSet<>(fetchAll(adapter, FIRST_SYNC).stream()
                .map(RawThreatRecord::rawValue)
                .toList());
    }
}
