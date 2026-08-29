package com.ctip.application.threat;

import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.application.ingestion.PublishNotPermittedException;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.EventPublisherPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.IndicatorRepository;
import com.ctip.application.port.ThreatRepository;
import com.ctip.domain.event.DomainEvent;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.shared.Visibility;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.threat.ExternalReference;
import com.ctip.domain.threat.IndicatorRole;
import com.ctip.domain.threat.NewThreatCommand;
import com.ctip.domain.threat.Threat;
import com.ctip.domain.threat.ThreatId;
import com.ctip.domain.threat.ThreatStatus;
import com.ctip.sdk.Confidence;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Threat 的寫入編排(docs/spec/09-api.md §9.1「Threat — 寫入」;權限 {@code threat:manage})。
 *
 * <p><strong>H6 的唯一執行點</strong>(§2.3、ADR 0020):建立關聯時讀取該 Indicator 的 TLP,
 * 以 {@code Tlp.strictest} 收緊 Threat 的 TLP;Indicator 之後在多來源合併中再度收緊時,
 * 由 {@code IndicatorTlpTightened} 事件觸發 {@link #retightenForIndicator}。
 * H6 不是 domain 不變量——H5 禁止聚合持有 Indicator 物件,聚合內部拿不到那些 TLP。
 *
 * <p>作用域:一律只操作呼叫者租戶自己的 Threat。跨租戶(含 public tenant 的公開威脅)
 * 一律當作查無 → 404,與 IOC 讀取端點同一種「不洩漏存在性」的處理。
 */
@Service
public class ThreatService {

    private final ThreatRepository threats;
    private final IndicatorRepository indicators;
    private final EventPublisherPort events;
    private final ClockPort clock;
    private final IdGeneratorPort ids;

    public ThreatService(
            ThreatRepository threats,
            IndicatorRepository indicators,
            EventPublisherPort events,
            ClockPort clock,
            IdGeneratorPort ids) {
        this.threats = threats;
        this.indicators = indicators;
        this.events = events;
        this.clock = clock;
        this.ids = ids;
    }

    /**
     * 建立 Threat。TLP 與擁有權的規則<strong>與 IOC 手動提交(§9.7)完全相同</strong>:
     * 預設 {@code AMBER}(私有);{@code CLEAR}/{@code GREEN} 需要 {@code ioc:publish},
     * 且擁有者轉為 public tenant。
     *
     * <p>不共用這條規則的話,{@code CLEAR} 的租戶私有威脅會是一個誰都看不到的東西——
     * §7.7 的可見度只讓 public tenant 的 CLEAR/GREEN 對外可見(ADR 0027;與 ADR 0019 第 2 節同因)。
     *
     * @throws ThreatConflictException 同租戶內 (type, name) 已存在(H1)
     * @throws PublishNotPermittedException 未持 {@code ioc:publish} 卻要求 CLEAR/GREEN
     */
    @Transactional
    public Threat create(CreateThreatCommand command, AuthenticatedIdentity actor) {
        Tlp tlp = resolveTlp(command.tlp(), actor);
        TenantId owner = ownerFor(tlp, actor);
        String name = command.name() == null ? "" : command.name().trim();
        threats.findByIdentity(owner, command.type(), name).ifPresent(existing -> {
            throw new ThreatConflictException("此租戶已有同型別同名的 Threat(不變量 H1):" + name);
        });
        Instant now = clock.now();
        Instant firstSeen = command.firstSeen() == null ? now : command.firstSeen();
        Instant lastSeen = command.lastSeen() == null ? firstSeen : command.lastSeen();
        Threat threat = Threat.create(new NewThreatCommand(
                new ThreatId(ids.nextId()),
                owner,
                command.type(),
                name,
                command.aliases(),
                command.description(),
                command.severity() == null ? Severity.INFO : command.severity(),
                Confidence.of(command.confidence() == null ? 0 : command.confidence()),
                tlp,
                command.tags(),
                firstSeen,
                lastSeen));
        return saveAndPublish(threat);
    }

    /**
     * 建立或更新關聯,並在此強制 H6。
     *
     * @return 更新後的 Threat;Threat 或 Indicator 任一不可見時為 empty(API 層回 404)
     */
    @Transactional
    public Optional<Threat> linkIndicator(
            ThreatId id, IndicatorId indicatorId, IndicatorRole role, AuthenticatedIdentity actor) {
        Optional<Threat> found = ownedThreat(id, actor);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Visibility visibility = Visibility.authenticated(actor.tenantId());
        Optional<Indicator> indicator = indicators.findVisibleById(indicatorId, visibility);
        if (indicator.isEmpty()) {
            return Optional.empty();
        }
        Threat threat = found.get();
        threat.linkIndicator(indicatorId, role, clock.now());
        // H6:關聯了一個更嚴格的 Indicator,Threat 必須跟著收緊(絕不放寬)
        threat.tightenTlpTo(indicator.get().tlp());
        return Optional.of(saveAndPublish(threat));
    }

    /** @return 更新後的 Threat;Threat 不可見或本來就沒有這個關聯時為 empty(404) */
    @Transactional
    public Optional<Threat> unlinkIndicator(ThreatId id, IndicatorId indicatorId, AuthenticatedIdentity actor) {
        Optional<Threat> found = ownedThreat(id, actor);
        if (found.isEmpty() || !found.get().unlinkIndicator(indicatorId)) {
            return Optional.empty();
        }
        // TLP 不因解除關聯而放寬:收緊是單向的,放寬等於把已經散布出去的分級收回,做不到
        return Optional.of(saveAndPublish(found.get()));
    }

    /** @throws ThreatConflictException 同一 Threat 內 (sourceName, externalId) 重複(H4) */
    @Transactional
    public Optional<Threat> addExternalReference(
            ThreatId id, ExternalReference reference, AuthenticatedIdentity actor) {
        Optional<Threat> found = ownedThreat(id, actor);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Threat threat = found.get();
        try {
            threat.addExternalReference(reference);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ThreatConflictException(e.getMessage());
        }
        return Optional.of(saveAndPublish(threat));
    }

    /**
     * 狀態轉換(ACTIVE / DORMANT / RETIRED);{@code RETIRED} 是終態。
     *
     * @throws ThreatConflictException 已退役,或本來就是該狀態
     */
    @Transactional
    public Optional<Threat> changeStatus(ThreatId id, ThreatStatus status, AuthenticatedIdentity actor) {
        Optional<Threat> found = ownedThreat(id, actor);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Threat threat = found.get();
        try {
            threat.changeStatus(status);
        } catch (IllegalStateException e) {
            throw new ThreatConflictException(e.getMessage());
        }
        return Optional.of(saveAndPublish(threat));
    }

    /**
     * H6 的事後維持:某個 Indicator 的 TLP 被合併收緊後,所有關聯它的 Threat 一併收緊。
     *
     * <p>不看可見度也不看租戶——這是資料一致性,不是查詢:一個 AMBER 的 IOC 若留在 CLEAR 的
     * 公開威脅底下,H6 就只在建立關聯的那一刻成立過。
     *
     * <p>由 {@code IndicatorTlpTightened} 於 AFTER_COMMIT 觸發,因此必須是
     * {@code REQUIRES_NEW}:那個回呼仍在已提交交易的 synchronization 範圍內,
     * 用預設的 {@code REQUIRED} 會參與一個已結束的交易,收緊結果不落庫也不報錯。
     *
     * @return 實際被收緊的 Threat 數
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int retightenForIndicator(IndicatorId indicatorId, Tlp indicatorTlp) {
        List<Threat> linked = threats.findByLinkedIndicator(indicatorId);
        int tightened = 0;
        for (Threat threat : linked) {
            if (threat.tightenTlpTo(indicatorTlp)) {
                saveAndPublish(threat);
                tightened++;
            }
        }
        return tightened;
    }

    /** §9.7 的 TLP 規則,套用於 Threat(見 {@link #create})。 */
    private static Tlp resolveTlp(Tlp requested, AuthenticatedIdentity actor) {
        Tlp tlp = requested == null ? Tlp.AMBER : requested;
        if (tlp == Tlp.RED) {
            throw new IllegalArgumentException("TLP:RED 不進入平台");
        }
        if ((tlp == Tlp.CLEAR || tlp == Tlp.GREEN) && !actor.hasPermission("ioc:publish")) {
            throw new PublishNotPermittedException("Publishing to the public pool requires ioc:publish");
        }
        return tlp;
    }

    private static TenantId ownerFor(Tlp tlp, AuthenticatedIdentity actor) {
        return tlp == Tlp.CLEAR || tlp == Tlp.GREEN ? TenantId.PUBLIC : actor.tenantId();
    }

    /**
     * 可寫入的範圍:自家租戶的 Threat,或公開威脅但持有 {@code ioc:publish}
     * (公開池的策展與發布是同一個平台營運權限)。其餘一律當查無 → 404。
     */
    private Optional<Threat> ownedThreat(ThreatId id, AuthenticatedIdentity actor) {
        return threats.findById(id)
                .filter(threat -> threat.ownerTenantId().equals(actor.tenantId())
                        || (threat.ownerTenantId().isPublic() && actor.hasPermission("ioc:publish")));
    }

    private Threat saveAndPublish(Threat threat) {
        Threat saved = threats.save(threat);
        for (DomainEvent event : threat.pullEvents()) {
            events.publish(event);
        }
        return saved;
    }
}
