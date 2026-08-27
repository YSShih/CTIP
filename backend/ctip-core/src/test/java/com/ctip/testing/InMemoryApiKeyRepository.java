package com.ctip.testing;

import com.ctip.application.port.ApiKeyRepository;
import com.ctip.domain.identity.ApiKey;
import com.ctip.domain.identity.ApiKeyId;
import com.ctip.domain.identity.KeyPrefix;
import com.ctip.domain.tenant.TenantId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 測試用 in-memory ApiKeyRepository。countActive 以固定時鐘外的當下判定,由測試傳入。 */
public final class InMemoryApiKeyRepository implements ApiKeyRepository {

    private final Map<ApiKeyId, ApiKey> byId = new LinkedHashMap<>();
    private Instant now = FixedClockPort.DEFAULT_NOW;

    public void now(Instant instant) {
        this.now = instant;
    }

    @Override
    public Optional<ApiKey> findByPrefix(KeyPrefix prefix) {
        return byId.values().stream()
                .filter(key -> key.keyPrefix().equals(prefix))
                .findFirst();
    }

    @Override
    public Optional<ApiKey> findById(ApiKeyId id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public List<ApiKey> findByTenant(TenantId tenantId) {
        return byId.values().stream()
                .filter(key -> key.tenantId().equals(tenantId))
                .toList();
    }

    @Override
    public long countActive(TenantId tenantId) {
        return findByTenant(tenantId).stream().filter(key -> key.isUsable(now)).count();
    }

    @Override
    public ApiKey save(ApiKey apiKey) {
        byId.put(apiKey.id(), apiKey);
        return apiKey;
    }
}
