package com.ctip.domain.source;

import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.SourceType;
import com.ctip.sdk.Tlp;
import java.time.Duration;

/** Source 聚合的持久化快照(重建與寫出用;record 建構子自動豁免參數數限制)。 */
public record SourceSnapshot(
        SourceId id,
        SourceType sourceType,
        String displayName,
        Tlp defaultTlp,
        RedistributionPolicy redistributionPolicy,
        Reputation reputation,
        boolean enabled,
        boolean syncable,
        Duration recommendedInterval,
        SourceHealth health,
        String lastErrorMessage,
        String nextCursor,
        long totalRecordsIngested) {}
