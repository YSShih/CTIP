package com.ctip.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.slf4j.LoggerFactory;

/**
 * 攔截整個 root logger 的輸出,供安全測試條號 8(日誌不得出現任何 secret)使用。
 * 刻意把等級調到 TRACE——只在 INFO 以上檢查會漏掉 debug 期的洩漏。
 */
public final class LogCapture implements AutoCloseable {

    private final Logger root;
    private final ListAppender<ILoggingEvent> appender;
    private final Level originalLevel;

    private LogCapture() {
        this.root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        this.appender = new ListAppender<>();
        this.originalLevel = root.getLevel();
        appender.start();
        root.addAppender(appender);
        root.setLevel(Level.TRACE);
    }

    public static LogCapture start() {
        return new LogCapture();
    }

    /** 訊息與參數皆納入——參數化日誌的 secret 只會出現在參數陣列中。 */
    public String text() {
        return appender.list.stream()
                .map(event -> event.getFormattedMessage() + " " + Arrays.toString(event.getArgumentArray()))
                .collect(Collectors.joining("\n"));
    }

    @Override
    public void close() {
        root.setLevel(originalLevel);
        root.detachAppender(appender);
        appender.stop();
    }
}
