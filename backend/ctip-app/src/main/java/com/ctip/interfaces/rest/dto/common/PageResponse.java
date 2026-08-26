package com.ctip.interfaces.rest.dto.common;

import java.util.List;

/** cursor 分頁回應(docs/spec/09-api.md §9.3):無下一頁時 nextCursor = null 且 hasMore = false。 */
public record PageResponse<T>(List<T> items, String nextCursor, boolean hasMore) {}
