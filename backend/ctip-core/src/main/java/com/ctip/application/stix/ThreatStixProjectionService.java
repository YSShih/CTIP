package com.ctip.application.stix;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.StixObjectPort;
import com.ctip.application.port.StixRelationshipPort;
import com.ctip.application.port.ThreatRepository;
import com.ctip.domain.stix.StixRelationship;
import com.ctip.domain.stix.StixRelationshipProjector;
import com.ctip.domain.stix.StixThreatProjector;
import com.ctip.domain.threat.Threat;
import com.ctip.domain.threat.ThreatId;
import com.ctip.domain.threat.ThreatSnapshot;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Threat 的 STIX 投影(docs/spec/07-domain-intel.md §7.8.1 的 M2 範圍、§7.8.6 的失敗隔離)。
 *
 * <p>由 {@code ThreatUpdated} 事件於<strong>交易提交後</strong>觸發:投影是衍生資料,
 * 失敗只記錄、不得讓寫入端點失敗,也不得回滾已提交的 Threat 變更(可隨時重建)。
 *
 * <p><strong>{@code REQUIRES_NEW} 不可省。</strong> AFTER_COMMIT 的回呼仍在原交易的
 * synchronization 範圍內——EntityManager 還綁在執行緒上,但那個交易已經提交完畢。
 * 用預設的 {@code REQUIRED} 會「參與」一個已結束的交易:寫入不報錯、也不會落庫
 * (實測:malware 與 relationship 一列都沒有,連例外都沒有)。
 *
 * <p>{@code CAMPAIGN}／{@code THREAT_ACTOR}／{@code PHISHING_KIT} 在 M2 沒有 SDO(§7.8.1),
 * 因此也沒有 relationship 可產生——它們的關聯不會出現在匯出的 bundle 裡。
 */
@Service
public class ThreatStixProjectionService {

    private static final Logger log = LoggerFactory.getLogger(ThreatStixProjectionService.class);

    private final ThreatRepository threats;
    private final StixObjectPort stixObjects;
    private final StixRelationshipPort stixRelationships;
    private final ClockPort clock;

    public ThreatStixProjectionService(
            ThreatRepository threats,
            StixObjectPort stixObjects,
            StixRelationshipPort stixRelationships,
            ClockPort clock) {
        this.threats = threats;
        this.stixObjects = stixObjects;
        this.stixRelationships = stixRelationships;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void project(ThreatId id) {
        try {
            threats.findById(id).ifPresent(this::projectThreat);
        } catch (RuntimeException e) {
            log.warn("Threat 的 STIX 投影失敗,只記錄不影響已提交的變更(§7.8.6):{}", id.value(), e);
        }
    }

    private void projectThreat(Threat threat) {
        if (!threat.hasStixProjection()) {
            return;
        }
        ThreatSnapshot snapshot = threat.snapshot();
        String stixId = StixThreatProjector.stixId(snapshot);
        Instant now = clock.now();
        Instant created = stixObjects.findCreated(stixId).orElse(now);
        stixObjects.upsert(StixThreatProjector.project(snapshot, created, now));
        // 關聯以 target(本 Threat)為單位整批同步:解除的關聯必須跟著消失,否則匯出的
        // bundle 會宣稱一個早已解除的關聯仍然成立
        List<StixRelationship> relationships = snapshot.indicators().stream()
                .map(link -> StixRelationshipProjector.project(snapshot, link, created, now))
                .toList();
        stixRelationships.syncForTarget(stixId, relationships);
    }
}
