package com.ctip.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.port.ClockPort;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** CachePort 的行程內實作:TTL 到期即視為未命中,evict 立即生效。 */
@Tag("unit")
class InMemoryCacheTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-08-29T00:00:00Z"));
    private final ClockPort clock = now::get;
    private final InMemoryCache cache = new InMemoryCache(clock);

    @Test
    void storedValueIsReadableUntilItExpires() {
        cache.put("k", "v", Duration.ofSeconds(60));
        assertThat(cache.get("k")).contains("v");

        now.set(now.get().plusSeconds(59));
        assertThat(cache.get("k")).contains("v");

        now.set(now.get().plusSeconds(1));
        assertThat(cache.get("k")).isEmpty();
    }

    @Test
    void evictRemovesImmediately() {
        cache.put("k", "v", Duration.ofSeconds(60));
        cache.evict("k");
        assertThat(cache.get("k")).isEmpty();
    }

    @Test
    void missingKeyIsEmptyNotAnError() {
        assertThat(cache.get("never-written")).isEmpty();
    }
}
