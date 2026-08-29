package com.ctip.domain.notification;

import com.ctip.sdk.IocType;
import com.ctip.sdk.Severity;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * 訂閱過濾條件(docs/spec/03-diagrams.md §3.2.9)。四個維度<strong>各自</strong>「空 = 不限」,
 * 非空時取交集;{@code minSeverity} 為 null 代表不限。
 *
 * <p>不變量 W5:過濾必須在伺服器端執行——{@link #accepts(NotificationEvent)} 是唯一的判定點,
 * 送達路徑不得先把全部事件推出去再由 client 篩。
 */
public record WebhookFilter(Set<IocType> iocTypes, Severity minSeverity, Set<String> tags, Set<UUID> sourceIds) {

    public WebhookFilter {
        iocTypes = iocTypes == null ? Set.of() : Set.copyOf(iocTypes);
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        sourceIds = sourceIds == null ? Set.of() : Set.copyOf(sourceIds);
    }

    /** 完全不過濾。 */
    public static WebhookFilter unfiltered() {
        return new WebhookFilter(Set.of(), null, Set.of(), Set.of());
    }

    public boolean accepts(NotificationEvent event) {
        return matchesIocType(event) && matchesSeverity(event) && matchesTags(event) && matchesSource(event);
    }

    /**
     * 指定了型別而事件不帶任何 IOC 型別(例:來源失敗、方案異動)時<strong>不通過</strong>:
     * 「只想收 IPV4 的新 IOC」不該連帶收到與 IOC 型別無關的平台通知。
     */
    private boolean matchesIocType(NotificationEvent event) {
        return iocTypes.isEmpty() || !Collections.disjoint(iocTypes, event.iocTypes());
    }

    private boolean matchesSeverity(NotificationEvent event) {
        return minSeverity == null || event.severity().ordinal() >= minSeverity.ordinal();
    }

    private boolean matchesTags(NotificationEvent event) {
        return tags.isEmpty() || !Collections.disjoint(tags, event.tags());
    }

    private boolean matchesSource(NotificationEvent event) {
        return sourceIds.isEmpty() || !Collections.disjoint(sourceIds, event.sourceIds());
    }
}
