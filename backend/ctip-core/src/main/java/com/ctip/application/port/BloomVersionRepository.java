package com.ctip.application.port;

import com.ctip.domain.bloom.BloomScope;
import com.ctip.domain.bloom.BloomVersion;
import com.ctip.domain.bloom.BloomVersionId;
import com.ctip.domain.tenant.TenantId;
import java.util.List;
import java.util.Optional;

/** BloomVersion 聚合的持久化 port(docs/spec/04-data-dictionary.md 表 22、23)。 */
public interface BloomVersionRepository {

    BloomVersion save(BloomVersion version);

    /** 該 scope 最新的一份(full 或 delta),即 client 現在應該同步到的版本。 */
    Optional<BloomVersion> findLatest(BloomScope scope, TenantId tenantId);

    Optional<BloomVersion> findLatestFullSnapshot(BloomScope scope, TenantId tenantId);

    /** 某 dataset 內的全部 delta,依 bloomVersion 升序——重建現行位元陣列與計算鏈長皆用它。 */
    List<BloomVersion> findDeltaChain(BloomScope scope, TenantId tenantId, long datasetVersion);

    /**
     * 該 scope 的版本,由新到舊最多 {@code limit} 筆。
     *
     * <p>保留政策(BLOOM_ARTIFACT_KEEP)在 application 層判定,不在 port:
     * 「保留最近 N 份」照字面會刪掉某個 dataset 的 full snapshot 卻留下它的 delta
     * (full 的 bloomVersion 最小、因此最舊),使那條鏈永遠無法重建。
     */
    List<BloomVersion> findNewestFirst(BloomScope scope, TenantId tenantId, int limit);

    void delete(BloomVersionId id);
}
