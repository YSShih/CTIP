package com.ctip.domain.indicator;

import com.ctip.domain.event.DomainEvent;
import com.ctip.domain.event.IndicatorEvents.IndicatorCreated;
import com.ctip.domain.event.IndicatorEvents.IndicatorExpired;
import com.ctip.domain.event.IndicatorEvents.IndicatorFalsePositiveReported;
import com.ctip.domain.event.IndicatorEvents.IndicatorMerged;
import com.ctip.domain.event.IndicatorEvents.IndicatorRevoked;
import com.ctip.domain.event.IndicatorEvents.IndicatorTlpTightened;
import com.ctip.domain.event.PendingEvents;
import com.ctip.domain.fingerprint.Fingerprint;
import com.ctip.domain.fingerprint.FingerprintStrategy;
import com.ctip.domain.source.Reputation;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.Confidence;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 核心聚合根,不變量 I1–I14 在此強制,不散落於 service 或 controller(docs/spec/02-ddd-model.md)。
 * I1(識別鍵唯一)由 ux_indicators_identity 與 repository 查詢共同強制;
 * I5–I11 的聚合計算集中於 {@link IndicatorMergePolicy}。
 * 重建後的 reputations 由後續合併呼叫補充,缺席者以中性值 50 計(§7.5)。
 */
public final class Indicator {

    private final IndicatorId id;
    private final TenantId ownerTenantId;
    private final IocValue value;
    private final Fingerprint fingerprint;
    private Instant firstSeen;
    private Instant lastSeen;
    private Instant validUntil;
    private Confidence confidence;
    private Severity severity;
    private int score;
    private Tlp tlp;
    private IndicatorStatus status;
    private Set<String> tags;
    private final List<IndicatorSource> sources = new ArrayList<>();
    private final List<HashRecord> hashRecords = new ArrayList<>();
    private final Map<SourceId, Reputation> reputations = new HashMap<>();
    private final PendingEvents pendingEvents = new PendingEvents();

    private Indicator(IndicatorSnapshot s) {
        this.id = Objects.requireNonNull(s.id());
        this.ownerTenantId = Objects.requireNonNull(s.ownerTenantId());
        this.value = Objects.requireNonNull(s.value());
        this.fingerprint = Objects.requireNonNull(s.fingerprint());
        this.firstSeen = Objects.requireNonNull(s.firstSeen());
        this.lastSeen = Objects.requireNonNull(s.lastSeen());
        this.validUntil = s.validUntil();
        this.confidence = Objects.requireNonNull(s.confidence());
        this.severity = Objects.requireNonNull(s.severity());
        this.score = s.score();
        this.tlp = Objects.requireNonNull(s.tlp());
        this.status = Objects.requireNonNull(s.status());
        this.tags = new HashSet<>(s.tags());
        s.sources().forEach(record -> sources.add(new IndicatorSource(record)));
        this.hashRecords.addAll(s.hashRecords());
        if (lastSeen.isBefore(firstSeen)) {
            throw new IllegalArgumentException("lastSeen 不得早於 firstSeen(不變量 I4)");
        }
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score 必須在 0–100 之間(不變量 I12)");
        }
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("Indicator 至少要有一筆來源記錄(不變量 I13)");
        }
    }

    /** 建立新 Indicator;fingerprint 一律對 normalizedValue 計算(不變量 I2)。 */
    public static Indicator create(NewIndicatorCommand cmd, FingerprintStrategy strategy) {
        Fingerprint fp = strategy.fingerprint(cmd.value().normalized());
        List<IndicatorSource> initial = List.of(new IndicatorSource(cmd.firstReport()));
        Map<SourceId, Reputation> reputations = Map.of(cmd.firstReport().sourceId(), cmd.sourceReputation());
        IndicatorSnapshot snapshot = new IndicatorSnapshot(
                cmd.id(),
                cmd.ownerTenantId(),
                cmd.value(),
                fp,
                IndicatorMergePolicy.aggregateFirstSeen(initial),
                IndicatorMergePolicy.aggregateLastSeen(initial),
                IndicatorMergePolicy.aggregateValidUntil(initial, cmd.value().type()),
                IndicatorMergePolicy.aggregateConfidence(initial, reputations),
                IndicatorMergePolicy.aggregateSeverity(initial),
                0,
                IndicatorMergePolicy.strictestTlp(initial),
                IndicatorMergePolicy.determineStatus(initial, reputations),
                IndicatorMergePolicy.unionTags(initial),
                List.of(cmd.firstReport()),
                List.of(new HashRecord(strategy.algorithm(), fp.hex(), null)));
        Indicator indicator = new Indicator(snapshot);
        indicator.reputations.putAll(reputations);
        indicator.pendingEvents.record(new IndicatorCreated(
                cmd.id(), cmd.ownerTenantId(), cmd.value().type(), cmd.value().normalized(), indicator.tlp));
        return indicator;
    }

    public static Indicator reconstitute(IndicatorSnapshot snapshot) {
        return new Indicator(snapshot);
    }

    /**
     * 多來源合併(重建後的聚合):合併前補入既有來源的信譽——重建後 reputations 為空,
     * 缺席者在聚合公式中以中性值 50 計(§7.5),pipeline 必須把所有涉及來源的信譽傳入。
     */
    public void mergeFrom(IndicatorSource report, Reputation sourceReputation, Map<SourceId, Reputation> known) {
        reputations.putAll(known);
        mergeFrom(report, sourceReputation);
    }

    /** 多來源合併:同來源 UPSERT、跨來源新增,再依 I5–I11 重新聚合。 */
    public void mergeFrom(IndicatorSource report, Reputation sourceReputation) {
        reputations.put(report.sourceId(), sourceReputation);
        sources.stream()
                .filter(r -> r.sourceId().equals(report.sourceId()))
                .findFirst()
                .ifPresentOrElse(existing -> existing.mergeReport(report), () -> sources.add(report));
        recompute();
        pendingEvents.record(new IndicatorMerged(id, ownerTenantId, report.sourceId()));
    }

    /** 過期轉換(04 §4.6 排程);前提為 I6 的 validUntil 已過,否則拒絕。 */
    public void markExpired(Instant now) {
        if (validUntil == null || !validUntil.isBefore(now)) {
            throw new IllegalStateException("validUntil 尚未到期,不得標記 EXPIRED(不變量 I6)");
        }
        sources.forEach(IndicatorSource::markExpired);
        this.status = IndicatorStatus.EXPIRED;
        pendingEvents.record(new IndicatorExpired(id, ownerTenantId, now));
    }

    /** I11 規則 1:僅信譽 >= 80 的來源可撤回。 */
    public void revoke(SourceId by, Reputation reputation) {
        if (!reputation.isTrustedForRetraction()) {
            throw new IllegalArgumentException("reputation < 80 的來源不可信任撤回(I11 規則 1)");
        }
        reputations.put(by, reputation);
        requireRecord(by).retract();
        recompute();
        pendingEvents.record(new IndicatorRevoked(id, ownerTenantId, by));
    }

    /**
     * I11 規則 2:標記誤判後由判定順序決定最終狀態,非呼叫端指定。§9.7:該來源的記錄
     * <strong>不存在則建立</strong>——誤判回報的來源是 MANUAL,而被回報的 IOC 通常來自別的來源,
     * 要求記錄必先存在會讓端點對絕大多數 IOC 直接失敗(ADR 0019 附註);新建的記錄一開始就是
     * FALSE_POSITIVE。{@code report} 已存在時只取 sourceId,{@code reputation} 參與 I11 的判定。
     */
    public void reportFalsePositive(IndicatorSourceSnapshot report, Reputation reputation) {
        SourceId by = report.sourceId();
        reputations.put(by, reputation);
        sources.stream()
                .filter(r -> r.sourceId().equals(by))
                .findFirst()
                .ifPresentOrElse(
                        IndicatorSource::markFalsePositive, () -> sources.add(IndicatorSource.falsePositive(report)));
        recompute();
        if (status == IndicatorStatus.FALSE_POSITIVE) {
            pendingEvents.record(new IndicatorFalsePositiveReported(id, ownerTenantId, by));
        }
    }

    /** 可見度(07 §7.7):自家資料全可見;public tenant 資料依 maxTlp。 */
    public boolean isVisibleTo(Tlp maxTlp, TenantId viewer) {
        if (ownerTenantId.equals(viewer)) {
            return true;
        }
        return ownerTenantId.isPublic() && tlp.isNoStricterThan(maxTlp);
    }

    /**
     * I14:全來源皆 INTERNAL_ONLY 時,不得出現在非擁有租戶的任何回應中。
     * 擁有租戶豁免僅限非 public 租戶(§7.9 作用域修正的安全解讀):匿名綁 public tenant,
     * 若 public 資料對「viewer == owner」豁免,再散布過濾對公開輸出將完全失效。
     */
    public boolean canBeRedistributedTo(TenantId viewer) {
        return (ownerTenantId.equals(viewer) && !ownerTenantId.isPublic()) || hasRedistributableSource();
    }

    /** public bloom 的資格(L7):ACTIVE 且 CLEAR 且可再散布。tenant 層見 BloomMembership(ADR 0019)。 */
    public boolean eligibleForBloom() {
        return status == IndicatorStatus.ACTIVE && tlp == Tlp.CLEAR && hasRedistributableSource();
    }

    private boolean hasRedistributableSource() {
        return sources.stream().anyMatch(r -> r.redistributionPolicy() != RedistributionPolicy.INTERNAL_ONLY);
    }

    private IndicatorSource requireRecord(SourceId sourceId) {
        return sources.stream()
                .filter(r -> r.sourceId().equals(sourceId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("此 Indicator 無該來源的記錄:" + sourceId));
    }

    private void recompute() {
        Tlp previousTlp = this.tlp;
        this.firstSeen = IndicatorMergePolicy.aggregateFirstSeen(sources);
        this.lastSeen = IndicatorMergePolicy.aggregateLastSeen(sources);
        this.validUntil = IndicatorMergePolicy.aggregateValidUntil(sources, value.type());
        this.confidence = IndicatorMergePolicy.aggregateConfidence(sources, reputations);
        this.severity = IndicatorMergePolicy.aggregateSeverity(sources);
        this.tlp = IndicatorMergePolicy.strictestTlp(sources);
        this.tags.addAll(IndicatorMergePolicy.unionTags(sources));
        this.status = IndicatorMergePolicy.determineStatus(sources, reputations);
        // 合併取最嚴格(§7.7):收緊時必須連帶收緊關聯的 Threat(H6),否則 H6 只在建立關聯當下成立
        if (previousTlp != tlp && previousTlp.isNoStricterThan(tlp)) {
            pendingEvents.record(new IndicatorTlpTightened(id, ownerTenantId, previousTlp, tlp));
        }
    }

    /** I12:score 0–100;由注入的 {@link ThreatScorer} 計算(§7.6),來源與信譽由聚合自身提供。 */
    public void applyScore(ThreatScorer scorer) {
        int computed = scorer.score(this, List.copyOf(sources), Map.copyOf(reputations));
        if (computed < 0 || computed > 100) {
            throw new IllegalArgumentException("score 必須在 0–100 之間(不變量 I12):" + computed);
        }
        this.score = computed;
    }

    public int score() {
        return score;
    }

    public List<DomainEvent> pullEvents() {
        return pendingEvents.pull();
    }

    public IndicatorSnapshot snapshot() {
        return new IndicatorSnapshot(
                id,
                ownerTenantId,
                value,
                fingerprint,
                firstSeen,
                lastSeen,
                validUntil,
                confidence,
                severity,
                score,
                tlp,
                status,
                Set.copyOf(tags),
                sources.stream().map(IndicatorSource::snapshot).toList(),
                List.copyOf(hashRecords));
    }

    public IndicatorId id() {
        return id;
    }

    public TenantId ownerTenantId() {
        return ownerTenantId;
    }

    public IocValue value() {
        return value;
    }

    public Fingerprint fingerprint() {
        return fingerprint;
    }

    public Confidence confidence() {
        return confidence;
    }

    public Severity severity() {
        return severity;
    }

    public Tlp tlp() {
        return tlp;
    }

    public IndicatorStatus status() {
        return status;
    }

    public Set<String> tags() {
        return Set.copyOf(tags);
    }

    public int sourceCount() {
        return IndicatorMergePolicy.activeSourceCount(sources);
    }
}
