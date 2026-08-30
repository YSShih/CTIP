package com.ctip.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.slf4j.LoggerFactory;

/**
 * 攔截整個 root logger 的輸出,供安全測試條號 8(日誌不得出現任何 secret)使用。
 * 刻意把等級調到 TRACE——只在 INFO 以上檢查會漏掉 debug 期的洩漏。
 *
 * <p>用自己的 appender 而不是 logback 的 {@code ListAppender}:後者收在普通的 ArrayList 裡,
 * 而本專案有多個背景執行緒會寫日誌(稽核寫入、Kafka 轉發、送達重試)——走訪它時
 * 會拿到 {@code ConcurrentModificationException},症狀看起來完全像是被測程式的問題。
 */
public final class LogCapture implements AutoCloseable {

    private final Logger root;
    private final ConcurrentAppender appender;
    private final Level originalLevel;

    private LogCapture() {
        this.root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        this.appender = new ConcurrentAppender();
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
        return List.copyOf(appender.lines).stream().collect(Collectors.joining("\n"));
    }

    @Override
    public void close() {
        root.setLevel(originalLevel);
        root.detachAppender(appender);
        appender.stop();
    }

    private static final class ConcurrentAppender extends AppenderBase<ILoggingEvent> {

        private final List<String> lines = new CopyOnWriteArrayList<>();

        @Override
        protected void append(ILoggingEvent event) {
            lines.add(event.getFormattedMessage() + " " + Arrays.toString(event.getArgumentArray()));
        }
    }
}
