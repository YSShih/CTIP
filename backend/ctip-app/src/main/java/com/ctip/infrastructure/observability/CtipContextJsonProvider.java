package com.ctip.infrastructure.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.Map;
import net.logstash.logback.composite.AbstractJsonProvider;
import tools.jackson.core.JsonGenerator;

/**
 * {@code service}、{@code environment} 與五個 MDC 關聯欄位(docs/spec/13-platform-ops.md §13.6)。
 *
 * <p>直接寫 MDC 的 provider 會在鍵不存在時<strong>省略欄位</strong>,而 §13.6 說的是「必含」——
 * 缺欄位與空值在下游(Loki / ES)的查詢是兩件事。因此這裡一律輸出,沒有值就是空字串。
 */
public class CtipContextJsonProvider extends AbstractJsonProvider<ILoggingEvent> {

    private final String service;
    private final String environment;

    public CtipContextJsonProvider(String service, String environment) {
        this.service = service;
        this.environment = environment;
    }

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) {
        generator.writeStringProperty(LogFields.SERVICE, service);
        generator.writeStringProperty(LogFields.ENVIRONMENT, environment);
        Map<String, String> mdc = event.getMDCPropertyMap();
        for (String field : LogFields.MDC_FIELDS) {
            generator.writeStringProperty(field, mdc.getOrDefault(field, ""));
        }
    }
}
