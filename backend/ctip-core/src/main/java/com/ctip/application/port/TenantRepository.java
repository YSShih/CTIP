package com.ctip.application.port;

import com.ctip.domain.tenant.Tenant;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.tenant.TenantSlug;
import java.util.List;
import java.util.Optional;

/** Tenant 持久化 port。 */
public interface TenantRepository {

    Optional<Tenant> findById(TenantId id);

    Optional<Tenant> findBySlug(TenantSlug slug);

    /** 全部租戶,依建立時間排序(管理端點 {@code GET /api/v1/admin/tenants};§9.1「管理」)。 */
    List<Tenant> findAll();

    Tenant save(Tenant tenant);
}
