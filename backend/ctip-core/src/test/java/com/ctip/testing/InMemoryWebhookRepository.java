package com.ctip.testing;

import com.ctip.application.port.WebhookRepository;
import com.ctip.domain.notification.Webhook;
import com.ctip.domain.notification.WebhookId;
import com.ctip.domain.notification.WebhookStatus;
import com.ctip.domain.tenant.TenantId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 測試用 in-memory WebhookRepository(application 單元測試;SQL 面由整合測試覆蓋)。 */
public final class InMemoryWebhookRepository implements WebhookRepository {

    private final Map<WebhookId, Webhook> store = new LinkedHashMap<>();

    @Override
    public Webhook save(Webhook webhook) {
        store.put(webhook.id(), webhook);
        return webhook;
    }

    @Override
    public Optional<Webhook> findById(WebhookId id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Webhook> findByTenant(TenantId tenantId) {
        return store.values().stream()
                .filter(webhook -> webhook.tenantId().equals(tenantId))
                .toList();
    }

    @Override
    public List<Webhook> findAllActive() {
        return store.values().stream()
                .filter(webhook -> webhook.status() == WebhookStatus.ACTIVE)
                .toList();
    }

    @Override
    public long countNotDisabled(TenantId tenantId) {
        return findByTenant(tenantId).stream()
                .filter(webhook -> webhook.status() != WebhookStatus.DISABLED)
                .count();
    }

    @Override
    public boolean delete(WebhookId id, TenantId tenantId) {
        return findById(id)
                .filter(webhook -> webhook.tenantId().equals(tenantId))
                .map(webhook -> store.remove(id) != null)
                .orElse(false);
    }
}
