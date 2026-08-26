package com.ctip.interfaces.rest.dto.ioc;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** POST /iocs/lookup(§9.1 批次精確驗證;11 §11.6):單次筆數上限超出回 413。 */
public record LookupRequest(@NotEmpty List<String> values) {}
