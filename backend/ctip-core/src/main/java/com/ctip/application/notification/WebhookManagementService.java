package com.ctip.application.notification;

import com.ctip.application.plan.QuotaService;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.SecureTokenGeneratorPort;
import com.ctip.application.port.WebhookRepository;
import com.ctip.domain.notification.HmacSecret;
import com.ctip.domain.notification.Webhook;
import com.ctip.domain.notification.WebhookId;
import com.ctip.domain.notification.WebhookSnapshot;
import com.ctip.domain.notification.WebhookStatus;
import com.ctip.domain.tenant.TenantId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Webhook 管理(09 §9.1 的三個 {@code /webhooks} 端點,權限 {@code webhook:manage})。
 *
 * <p>不變量 W6 在這裡強制:建立前先對 {@code plans.max_webhooks} 檢查
 * ——超限是<strong>非時間窗</strong>的能力上限,回 403 {@code PLAN_LIMIT_EXCEEDED}(§9.7)。
 */
@Service
public class WebhookManagementService {

    /** base62 40 碼 ≈ 238 bits,超過 HMAC-SHA256 需要的強度。 */
    private static final int SECRET_LENGTH = 40;

    private final WebhookRepository webhooks;
    private final QuotaService quotas;
    private final SecureTokenGeneratorPort tokens;
    private final IdGeneratorPort idGenerator;
    private final ClockPort clock;

    public WebhookManagementService(
            WebhookRepository webhooks,
            QuotaService quotas,
            SecureTokenGeneratorPort tokens,
            IdGeneratorPort idGenerator,
            ClockPort clock) {
        this.webhooks = webhooks;
        this.quotas = quotas;
        this.tokens = tokens;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /**
     * 建立並回傳一次性的密鑰原文(不變量 W2 的對外契約:原文只在建立當下回傳一次;
     * 之後 API 與 UI 都不再吐出,只有送達路徑會解密使用)。
     */
    @Transactional
    public IssuedWebhook register(NewWebhookCommand command) {
        quotas.requireWebhookHeadroom(command.tenantId(), webhooks.countNotDisabled(command.tenantId()));
        String secret = tokens.randomBase62(SECRET_LENGTH);
        Webhook webhook = Webhook.register(new WebhookSnapshot(
                new WebhookId(idGenerator.nextId()),
                command.tenantId(),
                command.createdBy(),
                command.name(),
                command.targetUrl(),
                new HmacSecret(secret),
                command.eventTypes(),
                command.filter(),
                WebhookStatus.ACTIVE,
                0,
                null,
                null,
                clock.now()));
        return new IssuedWebhook(webhooks.save(webhook), secret);
    }

    @Transactional(readOnly = true)
    public List<Webhook> list(TenantId tenantId) {
        return webhooks.findByTenant(tenantId);
    }

    /** @return false 表示該 id 不存在或不屬於此租戶(controller 回 404,不洩漏存在性) */
    @Transactional
    public boolean delete(WebhookId id, TenantId tenantId) {
        return webhooks.delete(id, tenantId);
    }

    /** 建立結果:聚合 + 只此一次的密鑰原文。 */
    public record IssuedWebhook(Webhook webhook, String secret) {}
}
