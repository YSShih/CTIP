package com.ctip.interfaces.rest.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 輪替請求。refresh token 單次使用;重複使用會撤銷整個 family(不變量 U5)。 */
public record RefreshRequest(
        @NotBlank @Size(max = 512) @Schema(example = "0G2f...")
        String refreshToken) {}
