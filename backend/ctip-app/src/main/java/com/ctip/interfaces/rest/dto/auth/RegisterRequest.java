package com.ctip.interfaces.rest.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 註冊請求(§9.1 認證端點)。註冊會同時建立一個 INDIVIDUAL 租戶並將使用者設為其管理者。 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) @Schema(example = "analyst@example.org")
        String email,

        @NotBlank @Size(min = 12, max = 256) @Schema(example = "correct-horse-battery")
        String password,

        @Size(max = 255) @Schema(example = "Alice Analyst") String displayName,
        @Size(max = 255) @Schema(example = "Example Org") String tenantName) {}
