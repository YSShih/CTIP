package com.ctip.domain.event;

import com.ctip.domain.source.SourceId;
import com.ctip.domain.tenant.TenantId;

/** Ingestion 流程事件(application 層發佈,Phase 6 起使用);平台範圍,tenantId 為 public tenant。 */
public interface IngestionEvents {

    record IngestionStarted(SourceId sourceId) implements DomainEvent {
        @Override
        public TenantId tenantId() {
            return TenantId.PUBLIC;
        }
    }

    record IngestionCompleted(
            SourceId sourceId, int recordsFetched, int recordsAccepted, int recordsRejected, int recordsMerged)
            implements DomainEvent {
        @Override
        public TenantId tenantId() {
            return TenantId.PUBLIC;
        }
    }

    record IngestionFailed(SourceId sourceId, String maskedReason) implements DomainEvent {
        @Override
        public TenantId tenantId() {
            return TenantId.PUBLIC;
        }
    }
}
