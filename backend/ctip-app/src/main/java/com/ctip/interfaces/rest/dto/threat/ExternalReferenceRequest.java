package com.ctip.interfaces.rest.dto.threat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新增外部參照(04 表 21)。{@code externalId} 與 {@code url} 至少要有一個(不變量 H3);
 * 兩者皆空由 domain 拒絕並映射為 400。
 */
public record ExternalReferenceRequest(
        @NotBlank @Size(max = 64) @Schema(example = "mitre-attack")
        String sourceName,

        @Size(max = 128) @Schema(example = "T1566") String externalId,

        @Size(max = 2048) @Schema(example = "https://attack.mitre.org/techniques/T1566/")
        String url,

        @Size(max = 1024) @Schema(example = "Phishing") String description) {}
