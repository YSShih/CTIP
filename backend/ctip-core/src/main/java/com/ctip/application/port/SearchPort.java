package com.ctip.application.port;

/**
 * 情資搜尋(M1 = PostgreSQL、M2 = Elasticsearch + 降級;docs/spec/13-platform-ops.md §13.7)。
 * 回傳 domain 自有的 CursorPage,不使用 Spring Data Page(ArchUnit 規則 8);
 * 亦不得出現任何搜尋引擎 client 型別(規則 11)。
 */
public interface SearchPort {

    SearchResult search(SearchQuery query);
}
