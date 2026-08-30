package com.ctip.interfaces.rest.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 變更密碼請求({@code POST /api/v1/auth/change-password})。
 * 長度限制與註冊一致(§10.4 的密碼政策由 {@code RawPassword} 強制)。
 */
public record ChangePasswordRequest(
        @NotBlank @Size(max = 72) @Schema(example = "old-correct-horse")
        String currentPassword,

        @NotBlank @Size(min = 12, max = 72) @Schema(example = "new-correct-horse-battery")
        String newPassword) {}
