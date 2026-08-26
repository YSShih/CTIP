package com.ctip.interfaces.rest.dto.ioc;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /iocs/search 的查詢條件(§9.1 複雜查詢;body 傳條件)。列舉值不合法回 400 INVALID_REQUEST。 */
public record SearchRequest(
        @NotBlank @Size(max = 2048) String query,
        String type,
        String severity,
        String status,
        String tlp,
        Boolean includeExpired,
        String cursor,
        Integer limit) {}
