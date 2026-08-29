package com.ctip.domain.threat;

import com.ctip.domain.event.DomainEvent;
import com.ctip.domain.event.PendingEvents;
import com.ctip.domain.event.ThreatEvents.ThreatUpdated;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.Confidence;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Threat 聚合根(docs/spec/02-ddd-model.md §2.3、03 §3.2.7)。
 *
 * <p>不變量:H1(識別鍵 ownerTenantId + type + name)由 {@code ux_threats_identity} 與
 * repository 查詢共同強制;H2、H3、H4、H5 在此強制。
 *
 * <p><strong>H6 不在此</strong>:「tlp 不得比任一關聯 Indicator 更寬鬆」是跨聚合條件,
 * 而 H5 禁止本聚合持有 Indicator 物件——聚合內部拿不到那些 TLP。H6 因此由 application 層在
 * 建立／變更關聯時以 {@link #tightenTlpTo(Tlp)} 收緊(ADR 0020;§2.3 已註記為應用層一致性規則)。
 */
public final class Threat {

    private final ThreatId id;
    private final TenantId ownerTenantId;
    private final ThreatType type;
    private final String name;
    private final Set<String> aliases;
    private String description;
    private Severity severity;
    private Confidence confidence;
    private Tlp tlp;
    private ThreatStatus status;
    private final Instant firstSeen;
    private Instant lastSeen;
    private final Set<String> tags;
    private final List<ThreatIndicatorLink> indicators = new ArrayList<>();
    private final List<ExternalReference> externalReferences = new ArrayList<>();
    private final PendingEvents pendingEvents = new PendingEvents();

    private Threat(ThreatSnapshot s) {
        this.id = Objects.requireNonNull(s.id(), "id 不得為 null");
        this.ownerTenantId = Objects.requireNonNull(s.ownerTenantId(), "ownerTenantId 不得為 null");
        this.type = Objects.requireNonNull(s.type(), "type 不得為 null");
        this.name = requireName(s.name());
        this.aliases = new LinkedHashSet<>(s.aliases());
        this.description = s.description();
        this.severity = Objects.requireNonNull(s.severity(), "severity 不得為 null");
        this.confidence = Objects.requireNonNull(s.confidence(), "confidence 不得為 null");
        this.tlp = Objects.requireNonNull(s.tlp(), "tlp 不得為 null");
        this.status = Objects.requireNonNull(s.status(), "status 不得為 null");
        this.firstSeen = Objects.requireNonNull(s.firstSeen(), "firstSeen 不得為 null");
        this.lastSeen = Objects.requireNonNull(s.lastSeen(), "lastSeen 不得為 null");
        this.tags = new LinkedHashSet<>(s.tags());
        this.indicators.addAll(s.indicators());
        this.externalReferences.addAll(s.externalReferences());
        if (lastSeen.isBefore(firstSeen)) {
            throw new IllegalArgumentException("lastSeen 不得早於 firstSeen(不變量 H2)");
        }
        requireDistinctReferences(externalReferences);
    }

    public static Threat create(NewThreatCommand cmd) {
        Threat threat = new Threat(new ThreatSnapshot(
                cmd.id(),
                cmd.ownerTenantId(),
                cmd.type(),
                cmd.name(),
                cmd.aliases(),
                cmd.description(),
                cmd.severity(),
                cmd.confidence(),
                cmd.tlp(),
                ThreatStatus.ACTIVE,
                cmd.firstSeen(),
                cmd.lastSeen(),
                cmd.tags(),
                List.of(),
                List.of()));
        threat.pendingEvents.record(new ThreatUpdated(threat.id, threat.ownerTenantId, ThreatChange.CREATED));
        return threat;
    }

    public static Threat reconstitute(ThreatSnapshot snapshot) {
        return new Threat(snapshot);
    }

    /**
     * 建立或更新與某個 Indicator 的關聯(H5:只記 id)。已存在時只改角色,{@code addedAt} 保持不變。
     *
     * @param at 關聯建立時間,同時推進 lastSeen(H2 由 max 保證)
     */
    public void linkIndicator(IndicatorId indicatorId, IndicatorRole role, Instant at) {
        Objects.requireNonNull(at, "at 不得為 null");
        requireNotRetired();
        Optional<ThreatIndicatorLink> existing = link(indicatorId);
        if (existing.isPresent()) {
            ThreatIndicatorLink current = existing.get();
            if (current.role() == role) {
                return;
            }
            indicators.set(indicators.indexOf(current), current.withRole(role));
        } else {
            indicators.add(new ThreatIndicatorLink(indicatorId, role, at));
        }
        if (at.isAfter(lastSeen)) {
            this.lastSeen = at;
        }
        pendingEvents.record(new ThreatUpdated(id, ownerTenantId, ThreatChange.INDICATOR_LINKED));
    }

    /** 解除關聯;沒有這個關聯時回 false(呼叫端映射為 404,不假成功)。 */
    public boolean unlinkIndicator(IndicatorId indicatorId) {
        Optional<ThreatIndicatorLink> existing = link(indicatorId);
        if (existing.isEmpty()) {
            return false;
        }
        indicators.remove(existing.get());
        pendingEvents.record(new ThreatUpdated(id, ownerTenantId, ThreatChange.INDICATOR_UNLINKED));
        return true;
    }

    /** H4:同一 Threat 內 (sourceName, externalId) 唯一;重複即拒絕(呼叫端映射為 409)。 */
    public void addExternalReference(ExternalReference reference) {
        Objects.requireNonNull(reference, "reference 不得為 null");
        requireNotRetired();
        boolean duplicate = externalReferences.stream()
                .anyMatch(existing -> existing.identityKey().equals(reference.identityKey()));
        if (duplicate) {
            throw new IllegalArgumentException("同一 Threat 內 (sourceName, externalId) 必須唯一(不變量 H4)");
        }
        externalReferences.add(reference);
        pendingEvents.record(new ThreatUpdated(id, ownerTenantId, ThreatChange.EXTERNAL_REFERENCE_ADDED));
    }

    /**
     * H6 的執行點(application 層呼叫):以 {@link Tlp#strictest} 收緊,<strong>永不放寬</strong>。
     * 關聯 Indicator 的 TLP 在多來源合併時會收緊,對應的 Threat 必須跟著收緊(ADR 0020)。
     *
     * @return 是否真的變更了 TLP
     */
    public boolean tightenTlpTo(Tlp indicatorTlp) {
        Tlp tightened = Tlp.strictest(tlp, Objects.requireNonNull(indicatorTlp, "indicatorTlp 不得為 null"));
        if (tightened == tlp) {
            return false;
        }
        this.tlp = tightened;
        pendingEvents.record(new ThreatUpdated(id, ownerTenantId, ThreatChange.TLP_TIGHTENED));
        return true;
    }

    /**
     * 狀態轉換(§4.5 的三態)。{@code RETIRED} 是<strong>終態</strong>:退役後不得回到
     * ACTIVE／DORMANT——把已宣告退役的威脅悄悄復活,會讓下游的快取與投影與平台說法不一致;
     * 要復活就建一個新的 Threat。已是同一狀態時視為衝突(呼叫端映射為 409),不假成功。
     */
    public void changeStatus(ThreatStatus newStatus) {
        Objects.requireNonNull(newStatus, "newStatus 不得為 null");
        requireNotRetired();
        if (status == newStatus) {
            throw new IllegalStateException("Threat 已經是 " + newStatus + " 狀態");
        }
        this.status = newStatus;
        pendingEvents.record(new ThreatUpdated(id, ownerTenantId, ThreatChange.STATUS_CHANGED));
    }

    /** §2.3 明列的行為;退役即 {@link #changeStatus} 到終態。 */
    public void retire() {
        changeStatus(ThreatStatus.RETIRED);
    }

    /** 只有 MALWARE_FAMILY 與 ATTACK_PATTERN 有對應的 STIX SDO(07 §7.8.1;M2 範圍)。 */
    public boolean hasStixProjection() {
        return type == ThreatType.MALWARE_FAMILY || type == ThreatType.ATTACK_PATTERN;
    }

    public ThreatSnapshot snapshot() {
        return new ThreatSnapshot(
                id,
                ownerTenantId,
                type,
                name,
                Set.copyOf(aliases),
                description,
                severity,
                confidence,
                tlp,
                status,
                firstSeen,
                lastSeen,
                Set.copyOf(tags),
                List.copyOf(indicators),
                List.copyOf(externalReferences));
    }

    public List<DomainEvent> pullEvents() {
        return pendingEvents.pull();
    }

    public ThreatId id() {
        return id;
    }

    public TenantId ownerTenantId() {
        return ownerTenantId;
    }

    public ThreatType type() {
        return type;
    }

    public String name() {
        return name;
    }

    public Tlp tlp() {
        return tlp;
    }

    public ThreatStatus status() {
        return status;
    }

    public List<ThreatIndicatorLink> indicators() {
        return List.copyOf(indicators);
    }

    public List<ExternalReference> externalReferences() {
        return List.copyOf(externalReferences);
    }

    private Optional<ThreatIndicatorLink> link(IndicatorId indicatorId) {
        Objects.requireNonNull(indicatorId, "indicatorId 不得為 null");
        return indicators.stream()
                .filter(existing -> existing.indicatorId().equals(indicatorId))
                .findFirst();
    }

    private void requireNotRetired() {
        if (status == ThreatStatus.RETIRED) {
            throw new IllegalStateException("已退役的 Threat 不得再變更");
        }
    }

    private static String requireName(String name) {
        Objects.requireNonNull(name, "name 不得為 null");
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("name 不得為空白(識別鍵的一部分,不變量 H1)");
        }
        return trimmed;
    }

    private static void requireDistinctReferences(List<ExternalReference> references) {
        long distinct = references.stream()
                .map(ExternalReference::identityKey)
                .distinct()
                .count();
        if (distinct != references.size()) {
            throw new IllegalArgumentException("同一 Threat 內 (sourceName, externalId) 必須唯一(不變量 H4)");
        }
    }
}
