package com.ctip.application.port;

import com.ctip.domain.notification.Webhook;
import com.ctip.domain.notification.WebhookId;
import com.ctip.domain.tenant.TenantId;
import java.util.List;
import java.util.Optional;

/** Webhook 聚合的持久化 port(docs/spec/04-data-dictionary.md 表 24)。 */
public interface WebhookRepository {

    Webhook save(Webhook webhook);

    Optional<Webhook> findById(WebhookId id);

    List<Webhook> findByTenant(TenantId tenantId);

    /**
     * 所有 {@code ACTIVE} 的 webhook——送達扇出的候選集合。
     *
     * <p>不變量 W5 的實際落點在 {@link Webhook#matches}:候選集合取回後於伺服器端逐一判定,
     * 不得把事件推給 client 再篩。M1–M3 為單一實例、webhook 總量以 {@code plans.max_webhooks}
     * 封頂(最高 50/租戶),全量取回可接受;真的成為熱點時再加租戶維度的快取。
     */
    List<Webhook> findAllActive();

    /** 不變量 W6 的計數依據:未被系統停用者才佔用額度。 */
    long countNotDisabled(TenantId tenantId);

    /** @return false 表示該 id 不存在或不屬於此租戶 */
    boolean delete(WebhookId id, TenantId tenantId);
}
