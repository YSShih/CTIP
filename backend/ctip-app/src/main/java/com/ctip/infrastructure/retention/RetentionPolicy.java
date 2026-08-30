package com.ctip.infrastructure.retention;

/**
 * 五項 SQL 清理任務的保留期(天;docs/spec/13-platform-ops.md §13.4 的保留政策表)。
 * 第六項(Bloom artifact 保留最近 N 份)是份數而非天數,由 {@code BloomRetentionService} 承擔。
 */
public record RetentionPolicy(
        int auditDays, int rawPayloadDays, int rejectionDays, int deliveryDays, int indicatorDays) {}
