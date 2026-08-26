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
 */
public record SearchRequest(
        @NotBlank @Size(max = 2048) String query,
        String type,
        String severity,
        String status,
        String tlp,
        Boolean includeExpired,
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
