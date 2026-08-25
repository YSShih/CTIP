package com.ctip.sdk;

import java.time.Duration;
import java.util.Set;

/**
 * 來源自我描述(docs/spec/08-ingestion-sdk.md §8.1)。
 * defaultTlp 與 redistributionPolicy 是 ingestion 快照進 indicator_sources 的法遵輸入(§7.9)。
 */
public record SourceMetadata(
        String displayName,
        String description,
        String homepageUrl,
        Set<IocType> supportedIocTypes,
        Tlp defaultTlp,
        RedistributionPolicy redistributionPolicy,
        Duration recommendedInterval,
        boolean requiresCredentials) {}
