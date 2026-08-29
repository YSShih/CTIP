package com.ctip.interfaces.rest.dto.threat;

import com.ctip.domain.threat.ThreatStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 變更 Threat 狀態(04 §4.5 的三態)。{@code RETIRED} 是終態,退役後不接受任何狀態變更;
 * 已經是該狀態時回 409(不假成功)。
 */
public record ThreatStatusRequest(
        @NotNull @Schema(example = "RETIRED") ThreatStatus status) {}
