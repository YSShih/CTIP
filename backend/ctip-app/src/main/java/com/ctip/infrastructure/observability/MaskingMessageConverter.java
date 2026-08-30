package com.ctip.infrastructure.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;

/**
 * 純文字格式(mvp / dev 的 console)的遮罩,與 JSON 格式共用 {@link SensitiveMasks} 的規則。
 * 在 {@code logback-spring.xml} 以 {@code %mask(...)} 包住訊息與例外堆疊。
 */
public class MaskingMessageConverter extends CompositeConverter<ILoggingEvent> {

    @Override
    protected String transform(ILoggingEvent event, String in) {
        return SensitiveMasks.apply(in);
    }
}
