package com.ctip.domain.indicator;

import com.ctip.sdk.IocType;
import java.time.Duration;

/** 型別預設 TTL(docs/spec/04-data-dictionary.md §4.6)。FILE_HASH 不過期(null)。 */
final class IocTtl {

    private IocTtl() {}

    static Duration defaultTtl(IocType type) {
        return switch (type) {
            case IPV4, IPV6 -> Duration.ofDays(30);
            case DOMAIN, URL, EMAIL -> Duration.ofDays(90);
            case FILE_HASH -> null;
        };
    }
}
