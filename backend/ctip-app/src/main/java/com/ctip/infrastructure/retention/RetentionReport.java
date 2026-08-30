package com.ctip.infrastructure.retention;

/** 一次全量保留清理的筆數(docs/spec/13-platform-ops.md §13.4 的六項)。 */
public record RetentionReport(
        int auditLogs,
        int rawPayloads,
        int rejections,
        int webhookDeliveries,
        int softDeletedIndicators,
        int bloomArtifacts) {}
