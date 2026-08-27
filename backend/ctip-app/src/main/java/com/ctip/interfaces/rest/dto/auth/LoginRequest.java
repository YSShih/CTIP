package com.ctip.interfaces.rest.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 登入請求。連續失敗達門檻會鎖定帳號(不變量 U7)。 */
public record LoginRequest(
        @NotBlank @Size(max = 320) @Schema(example = "analyst@example.org")
        String email,

        @NotBlank @Size(max = 256) @Schema(example = "correct-horse-battery")
        String password) {}
