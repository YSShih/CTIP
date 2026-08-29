package com.ctip.application.port;

import com.ctip.application.indicator.IndicatorFilter;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.shared.Cursor;
import com.ctip.domain.shared.CursorPage;
import com.ctip.domain.shared.Visibility;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.IocType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Indicator 持久化 port(docs/spec/01-architecture.md §1.6)。
 * findVisible* 一律套用統一的 tenant + TLP + 再散布過濾(§1.11);
 * 跨租戶不可見即查無(API 層映射為 404,避免資源存在性洩漏)。
 */
public interface IndicatorRepository {

    Optional<Indicator> findById(IndicatorId id);

    /** 以識別鍵查詢(不變量 I1:type + normalizedValue + ownerTenantId)。 */
    Optional<Indicator> findByIdentity(IocType type, String normalizedValue, TenantId ownerTenantId);

    Optional<Indicator> findVisibleById(IndicatorId id, Visibility visibility);

    /** 批次精確驗證用:同識別值於可見範圍內的記錄(自家優先,其次 public)。 */
    Optional<Indicator> findVisibleByIdentity(IocType type, String normalizedValue, Visibility visibility);

    /** 批次取可見的 Indicator(Threat 的關聯清單);不可見者不在結果中,不報錯、不洩漏存在性。 */
    List<Indicator> findVisibleByIds(List<IndicatorId> ids, Visibility visibility);

    CursorPage<Indicator> findVisible(Visibility visibility, IndicatorFilter filter, Cursor after, int limit);

    /** offset 分頁(§9.3:僅 UI 需要頁碼時;offset 上限由 API 層強制)。 */
    List<Indicator> findVisibleOffset(Visibility visibility, IndicatorFilter filter, int offset, int limit);

    /** 過期標記排程用(§7.10):status = ACTIVE 且 validUntil &lt; now,最多 limit 筆。 */
    List<Indicator> findExpirable(Instant now, int limit);

    Indicator save(Indicator indicator);
}
