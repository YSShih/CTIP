package com.ctip.domain.event;

import com.ctip.domain.source.SourceId;
import com.ctip.domain.tenant.TenantId;

/** Source 聚合發佈的 M1 事件;來源健康屬平台範圍,tenantId 為 public tenant。 */
public interface SourceEvents {

    record SourceDegraded(SourceId sourceId, int consecutiveFailures) implements DomainEvent {
        @Override
        public TenantId tenantId() {
            return TenantId.PUBLIC;
        }
    }

    record SourceFailed(SourceId sourceId, int consecutiveFailures) implements DomainEvent {
        @Override
        public TenantId tenantId() {
            return TenantId.PUBLIC;
        }
    }

    record SourceRecovered(SourceId sourceId) implements DomainEvent {
        @Override
        public TenantId tenantId() {
            return TenantId.PUBLIC;
        }
    }
}
