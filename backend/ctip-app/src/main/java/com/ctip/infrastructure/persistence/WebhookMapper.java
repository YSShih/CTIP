package com.ctip.infrastructure.persistence;

import com.ctip.application.port.SecretCipherPort;
import com.ctip.domain.notification.HmacSecret;
import com.ctip.domain.notification.NotificationType;
import com.ctip.domain.notification.Webhook;
import com.ctip.domain.notification.WebhookFilter;
import com.ctip.domain.notification.WebhookId;
import com.ctip.domain.notification.WebhookSnapshot;
import com.ctip.domain.notification.WebhookStatus;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.UserId;
import com.ctip.sdk.IocType;
import com.ctip.sdk.Severity;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Webhook domain ↔ JPA entity。
 *
 * <p>不是 MapStruct 介面:它需要 {@link SecretCipherPort} 這個相依(密鑰以 AES-GCM 加解密,
 * 不變量 W2 定調;ADR 0021),而 MapStruct 的 default 方法拿不到注入的協作者。
 */
@Component
class WebhookMapper {

    private final SecretCipherPort cipher;

    WebhookMapper(SecretCipherPort cipher) {
        this.cipher = cipher;
    }

    Webhook toDomain(WebhookEntity e) {
        return Webhook.reconstitute(new WebhookSnapshot(
                new WebhookId(e.id),
                new TenantId(e.tenantId),
                new UserId(e.createdByUserId),
                e.name,
                e.targetUrl,
                new HmacSecret(cipher.decrypt(e.secretEncrypted)),
                Arrays.stream(e.eventTypes)
                        .map(NotificationType::valueOf)
                        .collect(Collectors.toCollection(LinkedHashSet::new)),
                new WebhookFilter(
                        Arrays.stream(e.filterIocTypes)
                                .map(IocType::valueOf)
                                .collect(Collectors.toCollection(LinkedHashSet::new)),
                        e.filterMinSeverity == null ? null : Severity.valueOf(e.filterMinSeverity),
                        new LinkedHashSet<>(Set.of(e.filterTags)),
                        new LinkedHashSet<>(Set.of(e.filterSourceIds))),
                WebhookStatus.valueOf(e.status),
                e.consecutiveFailures,
                e.lastDeliveryAt,
                e.lastSuccessAt,
                e.createdAt));
    }

    void updateEntity(Webhook webhook, WebhookEntity e) {
        WebhookSnapshot s = webhook.snapshot();
        e.id = s.id().value();
        e.tenantId = s.tenantId().value();
        e.createdByUserId = s.createdByUserId().value();
        e.name = s.name();
        e.targetUrl = s.targetUrl();
        // 已加密過的密文不重新加密:AES-GCM 每次會用新的 nonce,重寫只是多一次 IO,
        // 但也不能省略——新建立的 webhook 必須寫入密文。以「entity 尚無密文」判斷。
        if (e.secretEncrypted == null) {
            e.secretEncrypted = cipher.encrypt(s.secret().value());
        }
        e.eventTypes = s.eventTypes().stream().map(Enum::name).toArray(String[]::new);
        e.filterIocTypes = s.filter().iocTypes().stream().map(Enum::name).toArray(String[]::new);
        e.filterMinSeverity = s.filter().minSeverity() == null
                ? null
                : s.filter().minSeverity().name();
        e.filterTags = s.filter().tags().toArray(String[]::new);
        e.filterSourceIds = s.filter().sourceIds().toArray(UUID[]::new);
        e.status = s.status().name();
        e.consecutiveFailures = (short) s.consecutiveFailures();
        e.lastDeliveryAt = s.lastDeliveryAt();
        e.lastSuccessAt = s.lastSuccessAt();
        e.createdAt = s.createdAt();
    }
}
