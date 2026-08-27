package com.ctip.application.identity;

import com.ctip.application.port.ApiKeyRepository;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.EventPublisherPort;
import com.ctip.application.port.RolePermissionRepository;
import com.ctip.domain.identity.ApiKey;
import com.ctip.domain.identity.ApiKeyId;
import com.ctip.domain.identity.IssuedApiKey;
import com.ctip.domain.tenant.TenantId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API key 的建立／撤銷／列出(docs/spec/10-identity-plans.md §10.5)。
 * 原文只在 {@link #issue} 回傳一次(不變量 K1);scope 不得超出建立者權限(K4)。
 */
@Service
public class ApiKeyService {

    private final ApiKeyRepository apiKeys;
    private final RolePermissionRepository rolePermissions;
    private final ApiKeyFactory factory;
    private final EventPublisherPort events;
    private final ClockPort clock;

    public ApiKeyService(
            ApiKeyRepository apiKeys,
            RolePermissionRepository rolePermissions,
            ApiKeyFactory factory,
            EventPublisherPort events,
            ClockPort clock) {
        this.apiKeys = apiKeys;
        this.rolePermissions = rolePermissions;
        this.factory = factory;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public IssuedApiKey issue(ApiKeyIssueRequest request, AuthenticatedIdentity creator) {
        if (!request.tenantId().equals(creator.tenantId())) {
            throw new IllegalArgumentException("不得為其他租戶建立 API key");
        }
        IssuedApiKey issued = factory.create(request, rolePermissions.allPermissionCodes(), creator.permissions());
        ApiKey saved = apiKeys.save(issued.apiKey());
        issued.apiKey().pullEvents().forEach(events::publish);
        return new IssuedApiKey(saved, issued.plaintext());
    }

    /** 跨租戶一律視為不存在(§9.4:回 404,不回 403)。 */
    @Transactional
    public void revoke(ApiKeyId id, TenantId tenantId) {
        ApiKey apiKey = apiKeys.findById(id)
                .filter(key -> key.tenantId().equals(tenantId))
                .orElseThrow(() -> new ApiKeyNotFoundException("API key not found"));
        apiKey.revoke(clock.now());
        apiKeys.save(apiKey);
        apiKey.pullEvents().forEach(events::publish);
    }

    @Transactional(readOnly = true)
    public List<ApiKey> list(TenantId tenantId) {
        return apiKeys.findByTenant(tenantId);
    }

    @Transactional(readOnly = true)
    public long countActive(TenantId tenantId) {
        return apiKeys.countActive(tenantId);
    }
}
