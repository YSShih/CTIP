package com.ctip.interfaces.rest;

import com.ctip.application.identity.AuthCommands;
import com.ctip.application.identity.AuthService;
import com.ctip.application.identity.AuthSession;
import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.application.identity.ClientInfo;
import com.ctip.interfaces.rest.dto.auth.AuthResponse;
import com.ctip.interfaces.rest.dto.auth.LoginRequest;
import com.ctip.interfaces.rest.dto.auth.RefreshRequest;
import com.ctip.interfaces.rest.dto.auth.RegisterRequest;
import com.ctip.interfaces.rest.dto.auth.SessionUserDto;
import com.ctip.interfaces.rest.openapi.AuthApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 認證端點(docs/spec/09-api.md §9.1)。四個端點皆為匿名可存取——它們是取得憑證的入口。
 * 所有規則在 {@link AuthService} 與其協作者中,controller 只做 DTO 轉換。
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController implements AuthApi {

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Override
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        AuthSession session = authService.register(
                new AuthCommands.Register(
                        request.email(), request.password(), request.displayName(), request.tenantName()),
                clientInfo(servletRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(session));
    }

    @Override
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        AuthSession session = authService.login(new AuthCommands.Login(
                request.email(),
                request.password(),
                servletRequest.getHeader("User-Agent"),
                servletRequest.getRemoteAddr()));
        return toResponse(session);
    }

    @Override
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest servletRequest) {
        AuthSession session = authService.refresh(new AuthCommands.Refresh(
                request.refreshToken(), servletRequest.getHeader("User-Agent"), servletRequest.getRemoteAddr()));
        return toResponse(session);
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    private static ClientInfo clientInfo(HttpServletRequest request) {
        return new ClientInfo(request.getHeader("User-Agent"), request.getRemoteAddr());
    }

    private static AuthResponse toResponse(AuthSession session) {
        AuthenticatedIdentity identity = session.identity();
        return new AuthResponse(
                session.accessToken(),
                session.refreshToken(),
                "Bearer",
                session.expiresInSeconds(),
                new SessionUserDto(
                        identity.userId().value().toString(),
                        identity.tenantId().value().toString(),
                        identity.role().name(),
                        identity.permissions(),
                        session.displayName()));
    }
}
