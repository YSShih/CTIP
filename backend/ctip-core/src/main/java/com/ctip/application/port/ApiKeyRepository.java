package com.ctip.application.port;

import com.ctip.domain.identity.ApiKey;
import com.ctip.domain.identity.ApiKeyId;
import com.ctip.domain.identity.KeyPrefix;
import com.ctip.domain.tenant.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** ApiKey 聚合的持久化 out-port。 */
public interface ApiKeyRepository {

    /** §10.5 驗證流程:以前綴定位單一列,再比對雜湊(避免全表雜湊比對)。 */
    Optional<ApiKey> findByPrefix(KeyPrefix prefix);

    Optional<ApiKey> findById(ApiKeyId id);

    List<ApiKey> findByTenant(TenantId tenantId);

    /** 未撤銷且未過期的數量;M2 由 ctip.api-key.max-per-tenant 承載,Phase 14 改讀 plans.max_api_keys。 */
    long countActive(TenantId tenantId);

    /**
     * 只更新 {@code last_used_at}(§10.5)。
     *
     * <p>刻意不走 {@link #save}:{@code save} 是整列覆寫,而 {@code touch} 手上的快照是認證那一刻讀的,
     * 若期間另一個請求撤銷了金鑰,整列回寫會把 {@code revoked_at} 覆寫回 null —— 撤銷被沖掉。
     * 這與 M1 複查抓到的 {@code IndicatorSource.mergeReport} 沖掉撤回是同一類缺陷(ADR 0013)。
     */
    void markUsed(ApiKeyId id, Instant usedAt);

    ApiKey save(ApiKey apiKey);
}
