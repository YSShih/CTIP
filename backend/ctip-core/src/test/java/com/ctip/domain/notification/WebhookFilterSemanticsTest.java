package com.ctip.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.IocType;
import com.ctip.sdk.Severity;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@link WebhookFilter} 的四個維度各自「空 = 不限」,非空時取交集
 * (docs/spec/03-diagrams.md §3.2.9)。
 */
@Tag("unit")
class WebhookFilterSemanticsTest {

    private static final UUID SOURCE_A = new UUID(0, 1);
    private static final UUID SOURCE_B = new UUID(0, 2);

    @Test
    void anEmptyFilterAcceptsEverything() {
        assertThat(WebhookFilter.unfiltered().accepts(event(Severity.INFO, Set.of(), Set.of(), Set.of())))
                .isTrue();
    }

    @Test
    void nullCollectionsBehaveAsEmpty() {
        WebhookFilter filter = new WebhookFilter(null, null, null, null);
        assertThat(filter.iocTypes()).isEmpty();
        assertThat(filter.tags()).isEmpty();
        assertThat(filter.sourceIds()).isEmpty();
        assertThat(filter.accepts(event(Severity.INFO, Set.of(), Set.of(), Set.of())))
                .isTrue();
    }

    @Test
    void minSeverityIsInclusive() {
        WebhookFilter filter = new WebhookFilter(Set.of(), Severity.HIGH, Set.of(), Set.of());
        assertThat(filter.accepts(event(Severity.HIGH, Set.of(), Set.of(), Set.of())))
                .isTrue();
        assertThat(filter.accepts(event(Severity.CRITICAL, Set.of(), Set.of(), Set.of())))
                .isTrue();
        assertThat(filter.accepts(event(Severity.MEDIUM, Set.of(), Set.of(), Set.of())))
                .isFalse();
    }

    @Test
    void tagsAndSourcesAreIntersections() {
        WebhookFilter byTag = new WebhookFilter(Set.of(), null, Set.of("botnet", "c2"), Set.of());
        assertThat(byTag.accepts(event(Severity.INFO, Set.of(), Set.of("c2", "phishing"), Set.of())))
                .isTrue();
        assertThat(byTag.accepts(event(Severity.INFO, Set.of(), Set.of("phishing"), Set.of())))
                .isFalse();

        WebhookFilter bySource = new WebhookFilter(Set.of(), null, Set.of(), Set.of(SOURCE_A));
        assertThat(bySource.accepts(event(Severity.INFO, Set.of(), Set.of(), Set.of(SOURCE_A, SOURCE_B))))
                .isTrue();
        assertThat(bySource.accepts(event(Severity.INFO, Set.of(), Set.of(), Set.of(SOURCE_B))))
                .isFalse();
    }

    /**
     * 指定了 IOC 型別而事件不帶任何型別(來源失敗、方案異動……)時不通過:
     * 「只想收 IPV4 的新 IOC」不該連帶收到與 IOC 型別無關的平台通知。
     */
    @Test
    void anIocTypeFilterExcludesEventsWithNoIocType() {
        WebhookFilter filter = new WebhookFilter(Set.of(IocType.IPV4), null, Set.of(), Set.of());
        assertThat(filter.accepts(event(Severity.INFO, Set.of(), Set.of(), Set.of())))
                .isFalse();
        assertThat(filter.accepts(event(Severity.INFO, Set.of(IocType.IPV4), Set.of(), Set.of())))
                .isTrue();
    }

    /** 平台事件對所有租戶可見,租戶自有的只對自己可見(§7.9 的 {@code IN (current, public)})。 */
    @Test
    void visibilityFollowsTheCurrentOrPublicRule() {
        TenantId mine = new TenantId(new UUID(0, 10));
        TenantId theirs = new TenantId(new UUID(0, 11));
        assertThat(eventOf(TenantId.PUBLIC).isVisibleTo(mine)).isTrue();
        assertThat(eventOf(mine).isVisibleTo(mine)).isTrue();
        assertThat(eventOf(theirs).isVisibleTo(mine)).isFalse();
    }

    private static NotificationEvent event(
            Severity severity, Set<IocType> iocTypes, Set<String> tags, Set<UUID> sourceIds) {
        return new NotificationEvent(
                new UUID(0, 5),
                NotificationType.NEW_IOC,
                new TenantId(new UUID(0, 10)),
                Instant.parse("2026-08-29T08:00:00Z"),
                null,
                "事件",
                null,
                severity,
                null,
                null,
                null,
                iocTypes,
                tags,
                sourceIds);
    }

    private static NotificationEvent eventOf(TenantId tenantId) {
        return new NotificationEvent(
                new UUID(0, 6),
                NotificationType.NEW_IOC,
                tenantId,
                Instant.parse("2026-08-29T08:00:00Z"),
                null,
                "事件",
                null,
                Severity.INFO,
                null,
                null,
                null,
                Set.of(),
                Set.of(),
                Set.of());
    }
}
