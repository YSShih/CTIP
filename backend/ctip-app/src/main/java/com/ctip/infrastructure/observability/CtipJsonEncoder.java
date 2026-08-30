package com.ctip.infrastructure.observability;

import net.logstash.logback.composite.loggingevent.LogLevelJsonProvider;
import net.logstash.logback.composite.loggingevent.LoggerNameJsonProvider;
import net.logstash.logback.composite.loggingevent.LoggingEventFormattedTimestampJsonProvider;
import net.logstash.logback.composite.loggingevent.LoggingEventThreadNameJsonProvider;
import net.logstash.logback.composite.loggingevent.MdcJsonProvider;
import net.logstash.logback.composite.loggingevent.MessageJsonProvider;
import net.logstash.logback.composite.loggingevent.StackTraceJsonProvider;
import net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder;
import net.logstash.logback.mask.MaskingJsonGeneratorDecorator;

/**
 * 結構化 JSON 日誌的編碼器(docs/spec/13-platform-ops.md §13.6)。
 *
 * <p>provider 清單寫在 Java 而不是 {@code logback-spring.xml}:九個必含欄位是規格的強制項,
 * 寫在這裡才有辦法用一個單元測試逐項驗證(XML 裡的設定只有啟動時才會被解讀)。
 * XML 只交出兩個值:{@code service} 與 {@code environment}。
 */
public class CtipJsonEncoder extends LoggingEventCompositeJsonEncoder {

    private String service = "ctip-backend";
    private String environment = "unknown";

    public void setService(String service) {
        this.service = service;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    @Override
    public void start() {
        if (getProviders().getProviders().isEmpty()) {
            configureProviders();
        }
        addDecorator(maskingDecorator());
        super.start();
    }

    private void configureProviders() {
        LoggingEventFormattedTimestampJsonProvider timestamp = new LoggingEventFormattedTimestampJsonProvider();
        timestamp.setFieldName(LogFields.TIMESTAMP);
        timestamp.setTimeZone("UTC");
        LogLevelJsonProvider level = new LogLevelJsonProvider();
        level.setFieldName(LogFields.LEVEL);
        MdcJsonProvider mdc = new MdcJsonProvider();
        // 五個關聯欄位由 CtipContextJsonProvider 保證恆存在,這裡只補其餘的 MDC
        LogFields.MDC_FIELDS.forEach(mdc::addExcludeMdcKeyName);

        getProviders().addProvider(timestamp);
        getProviders().addProvider(level);
        getProviders().addProvider(new CtipContextJsonProvider(service, environment));
        getProviders().addProvider(new LoggerNameJsonProvider());
        getProviders().addProvider(new LoggingEventThreadNameJsonProvider());
        getProviders().addProvider(new MessageJsonProvider());
        getProviders().addProvider(new StackTraceJsonProvider());
        getProviders().addProvider(mdc);
    }

    /**
     * 以程式加入的元件必須自己 {@code start()}——Joran 只會啟動 XML 裡宣告的子元件,
     * 漏掉這一步時 decorate 期的 delegate 是 null(實測 NPE)。
     */
    private static MaskingJsonGeneratorDecorator maskingDecorator() {
        MaskingJsonGeneratorDecorator masking = new MaskingJsonGeneratorDecorator();
        masking.addValueMasker(new SensitiveValueMasker());
        masking.start();
        return masking;
    }
}
