package com.ctip.interfaces.rest.openapi;

import com.ctip.interfaces.rest.dto.auth.AuthResponse;
import com.ctip.interfaces.rest.dto.auth.ChangePasswordRequest;
import com.ctip.interfaces.rest.dto.auth.ChangePasswordResponse;
import com.ctip.interfaces.rest.dto.auth.LoginRequest;
import com.ctip.interfaces.rest.dto.auth.RefreshRequest;
import com.ctip.interfaces.rest.dto.auth.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

/** 認證端點的 OpenAPI 文件(§9.1、§10.4);controller 實作本介面以繼承註解。 */
@Tag(name = "Auth", description = "Registration, login, refresh-token rotation and logout.")
public interface AuthApi {

    String SESSION_EXAMPLE = """
            {"accessToken":"eyJhbGciOiJIUzI1NiJ9.e30.sig","refreshToken":"0G2f8xQ...",\
            "tokenType":"Bearer","expiresIn":900,\
            "user":{"userId":"3f1b0c2e-9a4d-4c1a-8b77-2b0f1a9c5d10",\
            "tenantId":"8b1a9c33-2f4e-4d55-9f6a-0c1d2e3f4a5b","role":"TENANT_ADMIN",\
            "permissions":["ioc:read","ioc:submit"],"displayName":"Alice Analyst"}}""";

    @Operation(
            summary = "Register a new account",
            description = "Creates a user together with a new INDIVIDUAL tenant and enrols the user as its "
                    + "TENANT_ADMIN, then returns a session. Passwords are stored as BCrypt hashes only. "
                    + "認證:匿名(本端點用於取得憑證)。")
    @ApiResponse(
            responseCode = "201",
            description = "Account created and session issued",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuthResponse.class),
                            examples = @ExampleObject(value = SESSION_EXAMPLE)))
    @ApiResponse(responseCode = "400", description = "Validation failed (INVALID_REQUEST)")
    @ApiResponse(responseCode = "409", description = "Email already registered (CONFLICT)")
    @SecurityRequirements
    ResponseEntity<AuthResponse> register(RegisterRequest request, HttpServletRequest servletRequest);

    @Operation(
            summary = "Log in with email and password",
            description = "Returns an access token (15 minutes by default) and a single-use refresh token. "
                    + "Ten consecutive failures lock the account for fifteen minutes. "
                    + "認證:匿名(本端點用於取得憑證)。")
    @ApiResponse(
            responseCode = "200",
            description = "Session issued",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuthResponse.class),
                            examples = @ExampleObject(value = SESSION_EXAMPLE)))
    @ApiResponse(responseCode = "401", description = "Invalid credentials or locked account (UNAUTHENTICATED)")
    @SecurityRequirements
    AuthResponse login(LoginRequest request, HttpServletRequest servletRequest);

    @Operation(
            summary = "Rotate a refresh token",
            description = "Exchanges a refresh token for a new pair. The presented token is consumed; replaying "
                    + "an already-used token revokes the whole token family. "
                    + "認證:以請求主體中的 refresh token 認證,不需 Authorization 標頭。")
    @ApiResponse(
            responseCode = "200",
            description = "New session issued",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuthResponse.class),
                            examples = @ExampleObject(value = SESSION_EXAMPLE)))
    @ApiResponse(responseCode = "401", description = "Unknown, revoked, expired or reused token (UNAUTHENTICATED)")
    @SecurityRequirements
    AuthResponse refresh(RefreshRequest request, HttpServletRequest servletRequest);

    @Operation(
            summary = "Log out",
            description = "Revokes every refresh token in the presented token's family. Access tokens are "
                    + "short-lived and are not blacklisted. "
                    + "認證:以請求主體中的 refresh token 認證,不需 Authorization 標頭。")
    @ApiResponse(
            responseCode = "204",
            description = "Session revoked",
            content =
                    @Content(mediaType = "application/json", examples = @ExampleObject(name = "empty", value = "null")))
    @ApiResponse(responseCode = "401", description = "Unknown refresh token (UNAUTHENTICATED)")
    @SecurityRequirements
    ResponseEntity<Void> logout(RefreshRequest request);

    @Operation(
            summary = "Change the caller's password",
            description = "Verifies the current password, stores the new one, and revokes every refresh token "
                    + "family of that user — including the session that issued this request, so the caller must "
                    + "log in again. API key identities have no user and are rejected. "
                    + "認證:需要 Bearer JWT(不接受 X-API-Key);任何已登入的角色皆可變更自己的密碼。")
    @ApiResponse(
            responseCode = "200",
            description = "Password changed; the response reports how many sessions were revoked",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ChangePasswordResponse.class),
                            examples = @ExampleObject(value = "{\"revokedSessions\":2}")))
    @ApiResponse(responseCode = "400", description = "New password fails the password policy (INVALID_REQUEST)")
    @ApiResponse(responseCode = "401", description = "Current password is wrong (UNAUTHENTICATED)")
    @ApiResponse(responseCode = "403", description = "Anonymous caller, or an API key identity (FORBIDDEN)")
    @SecurityRequirement(name = "bearerAuth")
    ChangePasswordResponse changePassword(ChangePasswordRequest request);
}
