package com.ctip.domain.event;

import com.ctip.domain.bloom.BloomScope;
import com.ctip.domain.tenant.TenantId;

/**
 * BloomVersion 聚合發佈的 M2 事件(docs/spec/02-ddd-model.md §2.4)。
 * 消費者為 Notification(M3);M2 只發佈,程序內無 listener(13 §13.1)。
 */
public interface BloomEvents {

    /** 一份新的 full snapshot 已生成並寫出;delta 不發此事件(client 以 manifest 的版號比對即可)。 */
    record BloomSnapshotReady(
            TenantId tenantId, BloomScope scope, long datasetVersion, long bloomVersion, long memberCount)
            implements DomainEvent {}
}
