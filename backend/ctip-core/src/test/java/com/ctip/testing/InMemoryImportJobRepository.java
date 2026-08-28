package com.ctip.testing;

import com.ctip.application.ingestion.ImportJob;
import com.ctip.application.ingestion.ImportJobId;
import com.ctip.application.port.ImportJobRepository;
import com.ctip.domain.tenant.TenantId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** 測試用 import_jobs 表;查詢一律帶 tenantId(跨租戶視為不存在)。 */
public final class InMemoryImportJobRepository implements ImportJobRepository {

    private final Map<ImportJobId, ImportJob> store = new LinkedHashMap<>();

    @Override
    public Optional<ImportJob> find(ImportJobId id, TenantId tenantId) {
        return Optional.ofNullable(store.get(id)).filter(job -> job.tenantId().equals(tenantId));
    }

    @Override
    public ImportJob save(ImportJob job) {
        store.put(job.id(), job);
        return job;
    }

    public int size() {
        return store.size();
    }
}
