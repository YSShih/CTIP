package com.ctip.interfaces.rest.dto.ioc;

import com.ctip.sdk.IocHashType;
import com.ctip.sdk.IocType;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;

/**
 * 單筆手動提交(docs/spec/09-api.md §9.7 {@code POST /api/v1/iocs})。
 *
 * <p>刻意<strong>沒有</strong> {@code ownerTenantId} 欄位:歸屬由提交者身分決定,不可指定。
 * {@code tlp} 省略時為 {@code AMBER}(私有);{@code CLEAR}/{@code GREEN} 需要 {@code ioc:publish},
 * 且會把該 IOC 轉為 public tenant 所有(§9.7「ioc:publish 的語意」)。
 * {@code type} 省略時由平台推斷(§7.2)。
 */
public record IocSubmitRequest(
        @Schema(example = "IPV4") IocType type,

        @NotBlank @Size(max = 2048) @Schema(example = "203.0.113.5")
        String value,

        @Schema(example = "null") IocHashType hashType,
        @Min(0) @Max(100) @Schema(example = "80") Integer confidence,
        @Schema(example = "HIGH") Severity severity,
        @Schema(example = "AMBER") Tlp tlp,
        @Schema(example = "null") Instant validUntil,

        @ArraySchema(schema = @Schema(example = "internal-incident-2026-08"))
        Set<@Size(max = 64) String> tags,

        @Size(max = 1024) @Schema(example = "observed in phishing campaign")
        String note) {}
