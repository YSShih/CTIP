package com.ctip.infrastructure.persistence;

import com.ctip.domain.tenant.Tenant;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.tenant.TenantSlug;
import com.ctip.domain.tenant.TenantStatus;
import com.ctip.domain.tenant.TenantType;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

/** Tenant domain ↔ JPA entity(docs/spec/01-architecture.md §1.6:mapper 屬 adapter 職責)。 */
@Mapper(componentModel = "spring")
interface TenantMapper {

    default Tenant toDomain(TenantEntity e) {
        return Tenant.reconstitute(
                new TenantId(e.id),
                new TenantSlug(e.slug),
                e.name,
                TenantType.valueOf(e.type),
                TenantStatus.valueOf(e.status));
    }

    default void updateEntity(Tenant tenant, @MappingTarget TenantEntity e) {
        e.id = tenant.id().value();
        e.slug = tenant.slug().value();
        e.name = tenant.name();
        e.type = tenant.type().name();
        e.status = tenant.status().name();
    }
}
