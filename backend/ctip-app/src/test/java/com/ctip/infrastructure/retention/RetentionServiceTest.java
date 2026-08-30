package com.ctip.infrastructure.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ctip.application.bloom.BloomRetentionService;
import com.ctip.application.port.ClockPort;
import java.sql.Timestamp;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 六項保留任務的編排(docs/spec/13-platform-ops.md §13.4):
 * <strong>失敗不影響其他任務</strong>,而且每一項都回報清理筆數。
 */
@Tag("unit")
class RetentionServiceTest {

    @Test
    void everyTaskRunsEvenWhenOneOfThemFails() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Timestamp.class))).thenThrow(new IllegalStateException("模擬:清理失敗"));
        BloomRetentionService bloom = mock(BloomRetentionService.class);
        when(bloom.purgeAll()).thenReturn(3);
        ClockPort clock = () -> java.time.Instant.parse("2026-08-30T00:00:00Z");
        RetentionService service =
                new RetentionService(new RetentionTasks(jdbc, clock, new RetentionPolicy(180, 30, 30, 30, 365)), bloom);

        RetentionReport report = service.runAll();

        // 五個 SQL 任務全炸,第六個仍然跑了,而且整批沒有把例外丟出去
        assertThat(report.auditLogs()).isZero();
        assertThat(report.rawPayloads()).isZero();
        assertThat(report.bloomArtifacts()).isEqualTo(3);
        verify(bloom).purgeAll();
    }

    @Test
    void countsAreReportedPerTask() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Timestamp.class))).thenReturn(4);
        when(jdbc.update(anyString(), any(Timestamp.class), any(Timestamp.class)))
                .thenReturn(2);
        BloomRetentionService bloom = mock(BloomRetentionService.class);
        when(bloom.purgeAll()).thenReturn(0);
        ClockPort clock = () -> java.time.Instant.parse("2026-08-30T00:00:00Z");
        RetentionService service =
                new RetentionService(new RetentionTasks(jdbc, clock, new RetentionPolicy(180, 30, 30, 30, 365)), bloom);

        RetentionReport report = service.runAll();

        assertThat(report.auditLogs()).isEqualTo(4);
        assertThat(report.rejections()).isEqualTo(4);
        assertThat(report.webhookDeliveries()).isEqualTo(4);
        assertThat(report.rawPayloads()).isEqualTo(4);
        assertThat(report.softDeletedIndicators()).isEqualTo(2);
    }

    /** 分批:一批滿 10,000 就再來一批,直到不滿為止(§13.4 規則 1)。 */
    @Test
    void deletionContinuesUntilABatchIsNotFull() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Timestamp.class)))
                .thenReturn(RetentionTasks.BATCH_SIZE)
                .thenReturn(RetentionTasks.BATCH_SIZE)
                .thenReturn(7);
        ClockPort clock = () -> java.time.Instant.parse("2026-08-30T00:00:00Z");

        int cleaned = new RetentionTasks(jdbc, clock, new RetentionPolicy(180, 30, 30, 30, 365)).purgeAuditLogs();

        assertThat(cleaned).isEqualTo(2 * RetentionTasks.BATCH_SIZE + 7);
        verify(jdbc, org.mockito.Mockito.times(3)).update(anyString(), any(Timestamp.class));
        verify(jdbc, org.mockito.Mockito.never()).update(anyString(), anyInt());
    }
}
