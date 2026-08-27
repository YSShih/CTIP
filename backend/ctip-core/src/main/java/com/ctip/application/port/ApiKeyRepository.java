package com.ctip.application.port;

import com.ctip.domain.identity.ApiKey;
import com.ctip.domain.identity.ApiKeyId;
import com.ctip.domain.identity.KeyPrefix;
import com.ctip.domain.tenant.TenantId;
import java.util.List;
import java.util.Optional;

/** ApiKey 聚合的持久化 out-port。 */
public interface ApiKeyRepository {

    /** §10.5 驗證流程:以前綴定位單一列,再比對雜湊(避免全表雜湊比對)。 */
    Optional<ApiKey> findByPrefix(KeyPrefix prefix);

    Optional<ApiKey> findById(ApiKeyId id);

    List<ApiKey> findByTenant(TenantId tenantId);

    /** 未撤銷且未過期的數量,供 plans.max_api_keys 檢查(Phase 14)。 */
    long countActive(TenantId tenantId);

    ApiKey save(ApiKey apiKey);
}
