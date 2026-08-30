package com.ctip.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.OutputStreamAppender;
import com.ctip.infrastructure.observability.CtipJsonEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import org.slf4j.LoggerFactory;

/**
 * 直接驅動兩種日誌輸出格式(13 §13.6):JSON({@link CtipJsonEncoder})與純文字 pattern。
 * 測試不經 appender——那要改動全域 logger 設定,而這裡要驗的只是編碼結果。
 */
public final class LoggingFormats {

    private LoggingFormats() {}

    public static String encodeAsJson(String message) {
        return encodeAsJson(message, Map.of());
    }

    public static String encodeAsJson(String message, Map<String, String> mdc) {
        CtipJsonEncoder encoder = new CtipJsonEncoder();
        encoder.setContext(loggerContext());
        encoder.setService("ctip");
        encoder.setEnvironment("mvp");
        encoder.start();
        try {
            return new String(encoder.encode(event(message, mdc)), StandardCharsets.UTF_8);
        } finally {
            encoder.stop();
        }
    }

    /**
     * 走 root logger 上<strong>實際生效</strong>的 appender(mvp / dev 是 `logback-spring.xml` 的 PLAIN)。
     * 不自己組 layout:那樣只驗到轉換器類別本身,驗不到 XML 裡的 {@code %mask(...)} 有沒有真的掛上。
     */
    public static String encodeWithConfiguredAppender(String message) {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        for (Iterator<Appender<ILoggingEvent>> it = root.iteratorForAppenders(); it.hasNext(); ) {
            Appender<ILoggingEvent> appender = it.next();
            if (appender instanceof OutputStreamAppender<ILoggingEvent> stream && stream.getEncoder() != null) {
                return new String(stream.getEncoder().encode(event(message, Map.of())), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("root logger 上沒有帶 encoder 的 appender");
    }

    private static ILoggingEvent event(String message, Map<String, String> mdc) {
        LoggingEvent event = new LoggingEvent();
        event.setLoggerName(LoggingFormats.class.getName());
        event.setLevel(Level.INFO);
        event.setMessage(message);
        event.setTimeStamp(System.currentTimeMillis());
        event.setMDCPropertyMap(mdc);
        return event;
    }

    private static LoggerContext loggerContext() {
        return (LoggerContext) LoggerFactory.getILoggerFactory();
    }
}
