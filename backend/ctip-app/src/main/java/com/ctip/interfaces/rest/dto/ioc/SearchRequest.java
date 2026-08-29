package com.ctip.interfaces.rest.dto.ioc;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * POST /iocs/search 的查詢條件(§9.1 複雜查詢;body 傳條件;13 §13.7 搜尋欄位)。
 * 列舉值不合法回 400 INVALID_REQUEST;tags 為全部包含;
 * confidence/score/lastSeen 為閉區間,端點可省略。
 *
 * <p>{@code fuzzy} 啟用 typosquatting 用的模糊比對(§13.7「模糊查詢(僅 M2)」)。
 * 它<strong>只有 Elasticsearch 後端服務查詢時生效</strong>;降級為 PostgreSQL 時被忽略,
 * 呼叫端由回應標頭 {@code X-Search-Backend} 得知這件事。
 */
public record SearchRequest(
        @NotBlank @Size(max = 2048) String query,
        String type,
        String severity,
        String status,
        String tlp,
        Boolean includeExpired,
        Boolean fuzzy,
        List<@Size(max = 128) String> tags,
        UUID sourceId,
        Integer confidenceMin,
        Integer confidenceMax,
        Integer scoreMin,
        Integer scoreMax,
        Instant lastSeenFrom,
        Instant lastSeenTo,
        String cursor,
        Integer limit) {}
