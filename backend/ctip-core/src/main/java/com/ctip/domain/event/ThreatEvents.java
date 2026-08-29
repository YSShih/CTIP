package com.ctip.domain.event;

import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.threat.ThreatChange;
import com.ctip.domain.threat.ThreatId;

/** Threat 聚合發佈的 M2 事件(docs/spec/02-ddd-model.md §2.4)。 */
public interface ThreatEvents {

    /**
     * §2.4 對 Threat 只列了這一個事件,因此建立、關聯變更、TLP 收緊與退役都經由它,
     * 以 {@link ThreatChange} 區分——與其為每種變更新增一個規格沒有的事件型別,
     * 不如讓消費端(Search M2、Notification M3)以同一個事件重新載入聚合。
     */
    record ThreatUpdated(ThreatId threatId, TenantId tenantId, ThreatChange change) implements DomainEvent {}
}
