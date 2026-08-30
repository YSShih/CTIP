package com.ctip.infrastructure.audit;

import com.ctip.application.audit.AuditEvent;
import com.ctip.application.audit.AuditRecord;
import com.ctip.application.port.AuditLogPort;
import com.ctip.application.port.AuditPort;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.IdGeneratorPort;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 稽核寫入(docs/spec/13-platform-ops.md §13.5 規則 3:非同步 + 本地<strong>有界</strong>佇列,
 * 溢出記 ERROR,且<strong>不得</strong>使業務操作失敗)。
 *
 * <p>三道保證:
 * <ol>
 *   <li>{@link #record} 在業務執行緒上只做「物化 + 入列」,不碰資料庫。</li>
 *   <li>佇列有界:滿了就丟棄並記 ERROR。無界佇列在資料庫變慢時會把堆積吃光——
 *       那才是真的讓業務操作失敗(同 {@code KafkaEventForwarder} 的判斷)。</li>
 *   <li>整個 {@link #record} 包在 try/catch 內:連物化都不准把例外丟回業務路徑。</li>
 * </ol>
 */
public class AuditWriter implements AuditPort {

    private static final int QUEUE_CAPACITY = 10_000;

    private static final Logger log = LoggerFactory.getLogger(AuditWriter.class);

    private final AuditLogPort auditLogs;
    private final AuditContext context;
    private final ClockPort clock;
    private final IdGeneratorPort ids;
    private final ThreadPoolExecutor writing;

    /** 已交出但尚未寫完的筆數;{@link #awaitQuiescence} 據此判斷排空(佇列長度會有空窗)。 */
    private final AtomicLong pending = new AtomicLong();

    public AuditWriter(AuditLogPort auditLogs, AuditContext context, ClockPort clock, IdGeneratorPort ids) {
        this.auditLogs = auditLogs;
        this.context = context;
        this.clock = clock;
        this.ids = ids;
        this.writing = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                Thread.ofPlatform().name("ctip-audit-writer").factory(),
                (task, executor) -> {
                    pending.decrementAndGet();
                    log.error("稽核佇列已滿({} 筆),丟棄一筆稽核紀錄;資料庫是否無回應?", QUEUE_CAPACITY);
                });
    }

    @Override
    public void record(AuditEvent event) {
        try {
            AuditRecord row = context.materialize(event, ids.nextId(), clock.now());
            pending.incrementAndGet();
            writing.execute(() -> persist(row));
        } catch (RuntimeException e) {
            log.error("稽核紀錄無法建立,已放棄該筆(業務操作不受影響);action={}", event.action(), e);
        }
    }

    private void persist(AuditRecord row) {
        try {
            auditLogs.append(List.of(row));
        } catch (RuntimeException e) {
            log.error("稽核寫入失敗,已放棄該筆(業務操作不受影響);action={} id={}", row.action(), row.id(), e);
        } finally {
            pending.decrementAndGet();
        }
    }

    /** 關閉前把佇列排空:正常關機不該丟掉已經發生的稽核事實。 */
    @PreDestroy
    void shutdown() {
        writing.shutdown();
        try {
            if (!writing.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("稽核佇列在關機時仍有未寫出的紀錄");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 測試用:等到已交出的紀錄都寫完,讓斷言不必依賴時間。
     *
     * <p>判斷依據是自己的計數而不是 {@code getQueue().isEmpty() && getActiveCount() == 0}:
     * 工作被取出佇列到 activeCount 加一之間有一個空窗,兩者<strong>同時</strong>為零
     * 卻其實還沒寫完——那會讓測試偶發地讀到空表。
     */
    public void awaitQuiescence(long timeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (pending.get() > 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
    }
}
