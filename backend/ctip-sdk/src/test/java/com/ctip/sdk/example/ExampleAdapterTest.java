package com.ctip.sdk.example;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.sdk.FetchContext;
import com.ctip.sdk.FetchResult;
import com.ctip.sdk.IocType;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.Severity;
import com.ctip.sdk.SourceMetadata;
import com.ctip.sdk.Tlp;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * SDK 範例 adapter 的測試(DoD M3-22)。同時是 plugin-sdk.md「測試 adapter」一節的示範:
 * feed 內容以 {@link ExampleThreatSourceAdapter.FeedClient} 注入,不需要 HTTP、不需要 Spring。
 */
@Tag("unit")
class ExampleAdapterTest {

    private static final String FEED = """
            # value\ttype\tobservedAt\tconfidence\tseverity\ttags
            https://phish.example.invalid/login\tURL\t2026-08-01T00:00:00Z\t90\tHIGH\tphishing,credential-theft
            malware.example.invalid\tDOMAIN\t2026-08-02T00:00:00Z\t70\tMEDIUM\t
            198.51.100.7\tIPV4\t2026-08-03T00:00:00Z\t\t\t
            """;

    private static final Map<String, String> CONFIG = Map.of(ExampleThreatSourceAdapter.API_KEY, "test-key");

    private static ExampleThreatSourceAdapter adapter(String feed) {
        return new ExampleThreatSourceAdapter(apiKey -> {
            assertThat(apiKey).isEqualTo("test-key");
            return feed;
        });
    }

    private static FetchContext context(Instant since, String cursor, int maxRecords) {
        return new FetchContext(since, cursor, CONFIG, maxRecords);
    }

    @Test
    void metadataDeclaresTlpAndRedistributionPolicy() {
        SourceMetadata metadata = adapter(FEED).metadata();

        assertThat(metadata.displayName()).isEqualTo("Example Feed");
        assertThat(metadata.defaultTlp()).isEqualTo(Tlp.CLEAR);
        assertThat(metadata.redistributionPolicy()).isEqualTo(RedistributionPolicy.ATTRIBUTION_REQUIRED);
        assertThat(metadata.supportedIocTypes()).containsExactlyInAnyOrder(IocType.URL, IocType.DOMAIN, IocType.IPV4);
        assertThat(metadata.requiresCredentials()).isTrue();
    }

    @Test
    void parsesEveryFeedLineIntoARawRecord() {
        FetchResult result = adapter(FEED).fetch(context(null, null, 100));

        assertThat(result.records()).hasSize(3);
        assertThat(result.hasMore()).isFalse();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.records().getFirst().rawValue()).isEqualTo("https://phish.example.invalid/login");
        assertThat(result.records().getFirst().declaredType()).isEqualTo(IocType.URL);
        assertThat(result.records().getFirst().sourceSeverity()).isEqualTo(Severity.HIGH);
        assertThat(result.records().getFirst().tags()).containsExactlyInAnyOrder("phishing", "credential-theft");
    }

    @Test
    void leavesOptionalFieldsNullWhenTheSourceDidNotDeclareThem() {
        FetchResult result = adapter(FEED).fetch(context(null, null, 100));

        assertThat(result.records().get(2).sourceConfidence()).isNull();
        assertThat(result.records().get(2).sourceSeverity()).isNull();
        assertThat(result.records().get(2).validUntil()).isNull();
        assertThat(result.records().get(2).tags()).isEmpty();
    }

    @Test
    void sinceKeepsOnlyRecordsObservedAfterTheLastSuccessfulSync() {
        FetchResult result = adapter(FEED).fetch(context(Instant.parse("2026-08-01T00:00:00Z"), null, 100));

        assertThat(result.records()).hasSize(2);
        assertThat(result.records().getFirst().rawValue()).isEqualTo("malware.example.invalid");
    }

    @Test
    void maxRecordsCapsThePageAndTheCursorResumesWhereItStopped() {
        ExampleThreatSourceAdapter adapter = adapter(FEED);

        FetchResult first = adapter.fetch(context(null, null, 2));
        assertThat(first.records()).hasSize(2);
        assertThat(first.hasMore()).isTrue();
        assertThat(first.nextCursor()).isEqualTo("2");

        FetchResult second = adapter.fetch(context(null, first.nextCursor(), 2));
        assertThat(second.records()).hasSize(1);
        assertThat(second.hasMore()).isFalse();
        assertThat(second.nextCursor()).isNull();
    }

    @Test
    void sameContextYieldsAnEqualResult() {
        ExampleThreatSourceAdapter adapter = adapter(FEED);

        assertThat(adapter.fetch(context(null, null, 100))).isEqualTo(adapter.fetch(context(null, null, 100)));
    }

    @Test
    void commentsAndBlankLinesAreSkipped() {
        FetchResult result = adapter("# only a comment\n\n   \n").fetch(context(null, null, 100));

        assertThat(result.records()).isEmpty();
    }

    @Test
    void missingCredentialsFailLoudly() {
        FetchContext noCredentials = new FetchContext(null, null, Map.of(), 100);

        assertThatThrownBy(() -> adapter(FEED).fetch(noCredentials))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ExampleThreatSourceAdapter.API_KEY);
    }

    @Test
    void malformedLinesAbortTheBatchInsteadOfBeingSwallowed() {
        assertThatThrownBy(() -> adapter("bad\tURL\n").fetch(context(null, null, 100)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("格式錯誤");
    }

    @Test
    void nonPositiveMaxRecordsIsRejected() {
        assertThatThrownBy(() -> adapter(FEED).fetch(context(null, null, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aNullFeedClientIsRejectedAtConstruction() {
        assertThatThrownBy(() -> new ExampleThreatSourceAdapter(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
