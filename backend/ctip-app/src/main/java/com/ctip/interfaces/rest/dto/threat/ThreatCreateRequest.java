package com.ctip.interfaces.rest.dto.threat;

import com.ctip.domain.threat.ThreatType;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;

/**
 * 建立 Threat(docs/spec/09-api.md §9.1「Threat — 寫入」;權限 {@code threat:manage})。
 *
 * <p>刻意<strong>沒有</strong> {@code ownerTenantId} 欄位:歸屬由呼叫者身分決定,不可指定
 * (與 {@code POST /iocs} 同一條規則)。{@code tlp} 省略時為 {@code AMBER}(私有);
 * {@code CLEAR}/{@code GREEN} 需要 {@code ioc:publish},且擁有者轉為 public tenant(§9.7)。
 * 之後會被關聯 IOC 的 TLP 自動收緊(H6),永遠不會被放寬。
 */
public record ThreatCreateRequest(
        @NotNull @Schema(example = "MALWARE_FAMILY") ThreatType type,

        @NotBlank @Size(max = 255) @Schema(example = "AgentTesla")
        String name,

        @ArraySchema(schema = @Schema(example = "Agent Tesla"))
        Set<@Size(max = 255) String> aliases,

        @Size(max = 4096) @Schema(example = "Commodity infostealer distributed via phishing attachments.")
        String description,

        @Schema(example = "HIGH") Severity severity,
        @Min(0) @Max(100) @Schema(example = "70") Integer confidence,
        @Schema(example = "AMBER") Tlp tlp,

        @ArraySchema(schema = @Schema(example = "infostealer"))
        Set<@Size(max = 64) String> tags,

        @Schema(example = "2026-01-15T00:00:00Z") Instant firstSeen,
        @Schema(example = "2026-08-20T00:00:00Z") Instant lastSeen) {}
