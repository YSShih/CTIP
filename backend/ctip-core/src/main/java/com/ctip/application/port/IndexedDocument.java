package com.ctip.application.port;

import java.time.Instant;

/**
 * 索引中一筆文件的身分與版本,供 reconciliation 與資料庫比對
 * (docs/spec/13-platform-ops.md §13.7「比對 DB 與 ES 的筆數與版本」)。
 */
public record IndexedDocument(String documentId, Instant updatedAt) {}
