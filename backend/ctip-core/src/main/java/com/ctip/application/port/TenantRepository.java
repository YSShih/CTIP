package com.ctip.application.port;

import com.ctip.domain.tenant.Tenant;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.tenant.TenantSlug;
import java.util.Optional;

/** Tenant 持久化 port。 */
public interface TenantRepository {

    Optional<Tenant> findById(TenantId id);

    Optional<Tenant> findBySlug(TenantSlug slug);

    Tenant save(Tenant tenant);
}
