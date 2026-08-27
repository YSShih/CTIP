package com.ctip.interfaces.rest.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 認證回應。{@code refreshToken} 為原文,只在此回傳,伺服器只保存其 SHA-256 雜湊(§10.4);
 * 呼叫端必須安全保存,下次輪替後舊值即失效。
 */
public record AuthResponse(
        @Schema(example = "eyJhbGciOiJIUzI1NiJ9...") String accessToken,
        @Schema(example = "0G2f...") String refreshToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(example = "900") long expiresIn,
        SessionUserDto user) {}
