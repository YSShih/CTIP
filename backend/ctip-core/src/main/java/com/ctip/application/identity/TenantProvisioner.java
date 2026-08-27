package com.ctip.application.identity;

import com.ctip.application.port.EventPublisherPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.TenantMembershipRepository;
import com.ctip.application.port.TenantRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.tenant.Tenant;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.tenant.TenantSlug;
import com.ctip.domain.tenant.TenantType;
import com.ctip.domain.user.UserId;
import org.springframework.stereotype.Service;

/**
 * 註冊時為新使用者建立所屬租戶。§10.1 規定 {@code primary_tenant_id} 非 null 且不得為
 * public tenant,但 §9.1 的註冊端點未定義租戶來源——依此推導:一次註冊建立一個
 * {@code INDIVIDUAL} 租戶並將使用者指派為 {@code TENANT_ADMIN}(ADR 0012 決策 5)。
 */
@Service
public class TenantProvisioner {

    private final TenantRepository tenants;
    private final TenantMembershipRepository memberships;
    private final IdGeneratorPort idGenerator;
    private final EventPublisherPort events;

    public TenantProvisioner(
            TenantRepository tenants,
            TenantMembershipRepository memberships,
            IdGeneratorPort idGenerator,
            EventPublisherPort events) {
        this.tenants = tenants;
        this.memberships = memberships;
        this.idGenerator = idGenerator;
        this.events = events;
    }

    public Tenant provision(String desiredName, String fallbackName) {
        String base = TenantSlugs.sanitize(desiredName == null || desiredName.isBlank() ? fallbackName : desiredName);
        TenantSlug slug = uniqueSlug(base);
        String name = desiredName == null || desiredName.isBlank() ? slug.value() : desiredName;
        Tenant tenant = Tenant.create(new TenantId(idGenerator.nextId()), slug, name, TenantType.INDIVIDUAL);
        Tenant saved = tenants.save(tenant);
        tenant.pullEvents().forEach(events::publish);
        return saved;
    }

    public void enroll(TenantId tenantId, UserId userId, RoleCode role) {
        memberships.assign(tenantId, userId, role);
    }

    private TenantSlug uniqueSlug(String base) {
        if (!base.isEmpty() && base.length() >= 2) {
            TenantSlug candidate = new TenantSlug(base);
            if (tenants.findBySlug(candidate).isEmpty()) {
                return candidate;
            }
        }
        return TenantSlugs.withSuffix(base, idGenerator.nextId());
    }
}
