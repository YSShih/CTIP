package com.ctip.application.port;

import com.ctip.application.threat.ThreatFilter;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.shared.Cursor;
import com.ctip.domain.shared.CursorPage;
import com.ctip.domain.shared.Visibility;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.threat.Threat;
import com.ctip.domain.threat.ThreatId;
import com.ctip.domain.threat.ThreatType;
import java.util.List;
import java.util.Optional;

/**
 * Threat 持久化 port(docs/spec/01-architecture.md §1.6)。
 * findVisible* 一律套用 threats 的可見度述詞(§7.7 的 tenant + TLP;
 * 見 {@code ThreatSpecifications} 與 ADR 0027 對「threats 沒有再散布維度」的定調);
 * 跨租戶不可見即查無(API 層映射為 404,避免資源存在性洩漏)。
 */
public interface ThreatRepository {

    Optional<Threat> findById(ThreatId id);

    Optional<Threat> findVisibleById(ThreatId id, Visibility visibility);

    /** H1 的識別鍵查詢:(ownerTenantId, type, name)。 */
    Optional<Threat> findByIdentity(TenantId ownerTenantId, ThreatType type, String name);

    CursorPage<Threat> findVisible(Visibility visibility, ThreatFilter filter, Cursor after, int limit);

    /** H6 的重新收緊用:所有關聯到該 Indicator 的 Threat(不分租戶——一致性規則與可見度無關)。 */
    List<Threat> findByLinkedIndicator(IndicatorId indicatorId);

    Threat save(Threat threat);
}
