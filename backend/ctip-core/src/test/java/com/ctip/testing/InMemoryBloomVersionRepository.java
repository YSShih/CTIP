package com.ctip.testing;

import com.ctip.application.port.BloomVersionRepository;
import com.ctip.domain.bloom.BloomScope;
import com.ctip.domain.bloom.BloomVersion;
import com.ctip.domain.bloom.BloomVersionId;
import com.ctip.domain.tenant.TenantId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** 測試用 bloom_versions;排序語意與 JPA adapter 一致(dataset desc、bloomVersion desc)。 */
public final class InMemoryBloomVersionRepository implements BloomVersionRepository {

    private static final Comparator<BloomVersion> NEWEST_FIRST = Comparator.comparingLong(BloomVersion::datasetVersion)
            .thenComparingLong(BloomVersion::bloomVersion)
            .reversed();

    private final List<BloomVersion> versions = new ArrayList<>();

    @Override
    public BloomVersion save(BloomVersion version) {
        versions.removeIf(existing -> existing.id().equals(version.id()));
        versions.add(version);
        return version;
    }

    @Override
    public Optional<BloomVersion> findLatest(BloomScope scope, TenantId tenantId) {
        return scoped(scope, tenantId).stream().min(NEWEST_FIRST);
    }

    @Override
    public Optional<BloomVersion> findLatestFullSnapshot(BloomScope scope, TenantId tenantId) {
        return scoped(scope, tenantId).stream()
                .filter(BloomVersion::isFullSnapshot)
                .min(NEWEST_FIRST);
    }

    @Override
    public List<BloomVersion> findDeltaChain(BloomScope scope, TenantId tenantId, long datasetVersion) {
        return scoped(scope, tenantId).stream()
                .filter(version -> !version.isFullSnapshot() && version.datasetVersion() == datasetVersion)
                .sorted(Comparator.comparingLong(BloomVersion::bloomVersion))
                .toList();
    }

    @Override
    public List<BloomVersion> findNewestFirst(BloomScope scope, TenantId tenantId, int limit) {
        return scoped(scope, tenantId).stream()
                .sorted(NEWEST_FIRST)
                .limit(limit)
                .toList();
    }

    @Override
    public void delete(BloomVersionId id) {
        versions.removeIf(version -> version.id().equals(id));
    }

    private List<BloomVersion> scoped(BloomScope scope, TenantId tenantId) {
        return versions.stream()
                .filter(version ->
                        version.scope() == scope && version.tenantId().equals(tenantId))
                .toList();
    }
}
