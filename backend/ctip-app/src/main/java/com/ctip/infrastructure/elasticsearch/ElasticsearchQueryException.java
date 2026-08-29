package com.ctip.infrastructure.elasticsearch;

/**
 * Elasticsearch 查詢或寫入失敗。刻意是 unchecked:降級由 {@code FallbackSearchAdapter} 的
 * circuit breaker 依例外判定(docs/spec/13-platform-ops.md §13.7),把 checked exception
 * 一路宣告上去只會讓 {@code SearchPort} 沾上後端細節(ArchUnit 規則 11)。
 */
public class ElasticsearchQueryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ElasticsearchQueryException(String message, Throwable cause) {
        super(message, cause);
    }
}
