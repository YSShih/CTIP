package com.ctip.infrastructure.kafka;

import com.ctip.domain.identity.ApiKeyId;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.notification.WebhookId;
import com.ctip.domain.plan.SubscriptionId;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.threat.ThreatId;
import com.ctip.domain.user.TokenFamilyId;
import com.ctip.domain.user.UserId;
import java.util.UUID;
import java.util.function.Function;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * 事件 payload 的識別碼值物件序列化。
 *
 * <p>沒有這個模組,{@code IndicatorId} 會被序列化成 {@code {"value":"<uuid>"}}
 * ——事件 schema(docs/api/events/)是對外契約,不該把 Java 的包裝型別漏到線上格式。
 * 反過來也不能在 domain 上加 Jackson 標註:{@code com.ctip.domain..} 不得依賴
 * {@code tools.jackson..}(ArchUnit 規則 1)。
 *
 * <p>刻意<strong>逐一列舉</strong>而不用「單一元件的 record 一律展開」的反射規則:
 * 展開哪些型別是對外契約的一部分,新增事件時應該被迫在這裡做一次決定。
 */
final class ValueObjectJsonModule {

    private ValueObjectJsonModule() {}

    static SimpleModule create() {
        SimpleModule module = new SimpleModule("ctip-event-value-objects");
        module.addSerializer(TenantId.class, uuid(TenantId::value));
        module.addSerializer(IndicatorId.class, uuid(IndicatorId::value));
        module.addSerializer(SourceId.class, uuid(SourceId::value));
        module.addSerializer(ThreatId.class, uuid(ThreatId::value));
        module.addSerializer(UserId.class, uuid(UserId::value));
        module.addSerializer(TokenFamilyId.class, uuid(TokenFamilyId::value));
        module.addSerializer(ApiKeyId.class, uuid(ApiKeyId::value));
        module.addSerializer(SubscriptionId.class, uuid(SubscriptionId::value));
        module.addSerializer(WebhookId.class, uuid(WebhookId::value));
        return module;
    }

    private static <T> ValueSerializer<T> uuid(Function<T, UUID> extractor) {
        return new ValueSerializer<T>() {
            @Override
            public void serialize(T value, JsonGenerator gen, SerializationContext context) {
                gen.writeString(extractor.apply(value).toString());
            }
        };
    }
}
