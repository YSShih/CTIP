package com.ctip.domain.indicator;

import com.ctip.domain.source.SourceId;
import com.ctip.sdk.Confidence;
import com.ctip.sdk.IocType;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 某來源對某 Indicator 的一筆回報,Indicator 聚合的內部實體(docs/spec/02-ddd-model.md)。
 * 同來源再次回報時 UPSERT 本記錄(mergeReport);跨來源永不互相覆寫。
 * redistributionPolicy 為 ingestion 當下的快照,不隨 sources 變更。
 */
public final class IndicatorSource {

    private final SourceId sourceId;
    private final String sourceValue;
    private Confidence sourceConfidence;
    private Severity sourceSeverity;
    private Tlp sourceTlp;
    private final Instant sourceFirstSeen;
    private Instant sourceLastSeen;
    private Instant sourceValidUntil;
    private final RedistributionPolicy redistributionPolicy;
    private int reportCount;
    private SourceRecordStatus status;
    private final Set<String> tags;

    public IndicatorSource(IndicatorSourceSnapshot s) {
        this.sourceId = Objects.requireNonNull(s.sourceId(), "sourceId 不得為 null");
        this.sourceValue = Objects.requireNonNull(s.sourceValue(), "sourceValue 不得為 null");
        this.sourceConfidence = s.sourceConfidence();
        this.sourceSeverity = s.sourceSeverity();
        this.sourceTlp = Objects.requireNonNull(s.sourceTlp(), "sourceTlp 不得為 null");
        this.sourceFirstSeen = Objects.requireNonNull(s.sourceFirstSeen(), "sourceFirstSeen 不得為 null");
        this.sourceLastSeen = Objects.requireNonNull(s.sourceLastSeen(), "sourceLastSeen 不得為 null");
        this.sourceValidUntil = s.sourceValidUntil();
        this.redistributionPolicy = Objects.requireNonNull(s.redistributionPolicy(), "redistributionPolicy 不得為 null");
        this.reportCount = s.reportCount();
        this.status = Objects.requireNonNull(s.status(), "status 不得為 null");
        this.tags = s.tags() == null ? new HashSet<>() : new HashSet<>(s.tags());
        if (sourceLastSeen.isBefore(sourceFirstSeen)) {
            throw new IllegalArgumentException("sourceLastSeen 不得早於 sourceFirstSeen");
        }
        if (reportCount < 1) {
            throw new IllegalArgumentException("reportCount 必須 >= 1");
        }
    }

    /**
     * 三步過期計算的第 2 步(docs/spec/04-data-dictionary.md §4.6):
     * COALESCE(sourceValidUntil, sourceLastSeen + defaultTtl(type));FILE_HASH 的 TTL 為 null。
     */
    public Instant effectiveValidUntil(IocType type) {
        if (sourceValidUntil != null) {
            return sourceValidUntil;
        }
        Duration ttl = IocTtl.defaultTtl(type);
        return ttl == null ? null : sourceLastSeen.plus(ttl);
    }

    /**
     * 同來源再次回報:更新觀測值並累加 reportCount(UPSERT 語意)。
     * status 規則(§7.5 撤回語意依賴 RETRACTED 存續,不得被例行同步沖掉):
     * 新回報為 RETRACTED 一律生效;RETRACTED 與 FALSE_POSITIVE 不因後續 ACTIVE 回報復活
     * (前者對齊 STIX 2.1 revoked 的單向性,後者為使用者斷言);EXPIRED 因新觀測回到 ACTIVE。
     */
    void mergeReport(IndicatorSource newer) {
        if (!newer.sourceId.equals(sourceId)) {
            throw new IllegalArgumentException("mergeReport 僅限同一來源");
        }
        if (newer.sourceLastSeen.isAfter(sourceLastSeen)) {
            this.sourceLastSeen = newer.sourceLastSeen;
        }
        this.sourceConfidence = newer.sourceConfidence;
        this.sourceSeverity = newer.sourceSeverity;
        this.sourceTlp = newer.sourceTlp;
        this.sourceValidUntil = newer.sourceValidUntil;
        this.tags.addAll(newer.tags);
        this.reportCount++;
        if (newer.status == SourceRecordStatus.RETRACTED) {
            this.status = SourceRecordStatus.RETRACTED;
        } else if (this.status == SourceRecordStatus.EXPIRED) {
            this.status = SourceRecordStatus.ACTIVE;
        }
    }

    void retract() {
        this.status = SourceRecordStatus.RETRACTED;
    }

    void markFalsePositive() {
        this.status = SourceRecordStatus.FALSE_POSITIVE;
    }

    void markExpired() {
        if (status == SourceRecordStatus.ACTIVE) {
            this.status = SourceRecordStatus.EXPIRED;
        }
    }

    public IndicatorSourceSnapshot snapshot() {
        return new IndicatorSourceSnapshot(
                sourceId,
                sourceValue,
                sourceConfidence,
                sourceSeverity,
                sourceTlp,
                sourceFirstSeen,
                sourceLastSeen,
                sourceValidUntil,
                redistributionPolicy,
                reportCount,
                status,
                Set.copyOf(tags));
    }

    public SourceId sourceId() {
        return sourceId;
    }

    public Confidence sourceConfidence() {
        return sourceConfidence;
    }

    public Severity sourceSeverity() {
        return sourceSeverity;
    }

    public Tlp sourceTlp() {
        return sourceTlp;
    }

    public Instant sourceFirstSeen() {
        return sourceFirstSeen;
    }

    public Instant sourceLastSeen() {
        return sourceLastSeen;
    }

    public RedistributionPolicy redistributionPolicy() {
        return redistributionPolicy;
    }

    public int reportCount() {
        return reportCount;
    }

    public SourceRecordStatus status() {
        return status;
    }

    public Set<String> tags() {
        return Set.copyOf(tags);
    }
}
