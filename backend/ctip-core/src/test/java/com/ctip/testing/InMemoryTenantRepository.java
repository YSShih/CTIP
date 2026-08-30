package com.ctip.testing;

import com.ctip.application.port.TenantRepository;
import com.ctip.domain.tenant.Tenant;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.tenant.TenantSlug;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 測試用 in-memory TenantRepository。 */
public final class InMemoryTenantRepository implements TenantRepository {

    private final Map<TenantId, Tenant> byId = new LinkedHashMap<>();

    @Override
    public Optional<Tenant> findById(TenantId id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Tenant> findBySlug(TenantSlug slug) {
        return byId.values().stream().filter(t -> t.slug().equals(slug)).findFirst();
    }

    @Override
    public List<Tenant> findAll() {
        return List.copyOf(byId.values());
    }

    @Override
    public Tenant save(Tenant tenant) {
        byId.put(tenant.id(), tenant);
        return tenant;
    }
}
