package com.ctip.domain.shared;

import java.util.List;
import java.util.Objects;

/**
 * cursor 分頁結果,取代 Spring Data 的 Page(無 COUNT query;docs/spec/02-ddd-model.md §2.6)。
 * {@code nextCursor} 為編碼後的不透明字串,無下一頁時為 null。
 */
public record CursorPage<T>(List<T> items, String nextCursor, boolean hasMore) {

    public CursorPage {
        Objects.requireNonNull(items, "items 不得為 null");
        items = List.copyOf(items);
    }

    public static <T> CursorPage<T> lastPage(List<T> items) {
        return new CursorPage<>(items, null, false);
    }
}
