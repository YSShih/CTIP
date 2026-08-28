package com.ctip.application.port;

import com.ctip.application.ingestion.ImportJob;
import com.ctip.application.ingestion.ImportJobId;
import com.ctip.domain.tenant.TenantId;
import java.util.Optional;

/** import_jobs 的持久化 port(docs/spec/04-data-dictionary.md 表 18b;兩模型表)。 */
public interface ImportJobRepository {

    /** 跨租戶一律視為不存在(§9.4:404,不回 403)——故查詢一定帶 tenantId。 */
    Optional<ImportJob> find(ImportJobId id, TenantId tenantId);

    ImportJob save(ImportJob job);
}
