package com.ctip.interfaces.rest;

import com.ctip.application.identity.AuthCommands;
import com.ctip.application.identity.AuthService;
import com.ctip.application.identity.AuthSession;
import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.application.identity.ClientInfo;
import com.ctip.application.identity.PasswordChangeService;
import com.ctip.domain.audit.AuditActorType;
import com.ctip.domain.user.UserId;
import com.ctip.infrastructure.audit.AuditSignals;
import com.ctip.infrastructure.security.TenantContext;
import com.ctip.interfaces.rest.dto.auth.AuthResponse;
import com.ctip.interfaces.rest.dto.auth.ChangePasswordRequest;
import com.ctip.interfaces.rest.dto.auth.ChangePasswordResponse;
import com.ctip.interfaces.rest.dto.auth.LoginRequest;
import com.ctip.interfaces.rest.dto.auth.RefreshRequest;
import com.ctip.interfaces.rest.dto.auth.RegisterRequest;
import com.ctip.interfaces.rest.dto.auth.SessionUserDto;
import com.ctip.interfaces.rest.error.ApiException;
import com.ctip.interfaces.rest.error.ErrorCode;
import com.ctip.interfaces.rest.openapi.AuthApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final PasswordChangeService passwordChanges;
    private final TenantContext tenantContext;

    AuthController(AuthService authService, PasswordChangeService passwordChanges, TenantContext tenantContext) {
        this.authService = authService;
        this.passwordChanges = passwordChanges;
        this.tenantContext = tenantContext;
    }

    @Override
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        AuthSession session = authService.register(
                new AuthCommands.Register(
                        request.email(), request.password(), request.displayName(), request.tenantName()),
                clientInfo(servletRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(signalActor(session)));
    }

    @Override
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        AuthSession session = authService.login(new AuthCommands.Login(
                request.email(),
                request.password(),
                servletRequest.getHeader("User-Agent"),
                servletRequest.getRemoteAddr()));
        return toResponse(signalActor(session));
    }

    @Override
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest servletRequest) {
        AuthSession session = authService.refresh(new AuthCommands.Refresh(
                request.refreshToken(), servletRequest.getHeader("User-Agent"), servletRequest.getRemoteAddr()));
        return toResponse(signalActor(session));
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        UserId user = authService.logout(request.refreshToken());
        AuditSignals.actor(AuditActorType.USER, user.value(), tenantContext.tenantId());
        return ResponseEntity.noContent().build();
    }

    /**
     * 變更密碼。成功後該使用者的<strong>全部</strong> refresh token family 都被撤銷
     * (ADR 0015),包含發出這次請求的那一個——呼叫端必須重新登入。
     *
     * <p>API key 身分沒有使用者,不能改任何人的密碼。
     */
    @Override
    @PostMapping("/change-password")
    @PreAuthorize("!hasRole('ANONYMOUS')")
    public ChangePasswordResponse changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        AuthenticatedIdentity caller = tenantContext.requireIdentity();
        if (caller.userId() == null) {
            throw new ApiException(ErrorCode.FORBIDDEN, "API key identities cannot change a password");
        }
        return new ChangePasswordResponse(
                passwordChanges.change(caller.userId(), request.currentPassword(), request.newPassword()));
    }

    /** 稽核的 LOGIN / TOKEN_REFRESH 需要行為者,而這幾支端點本身是匿名的(§13.5)。 */
    private static AuthSession signalActor(AuthSession session) {
        AuthenticatedIdentity identity = session.identity();
        AuditSignals.actor(AuditActorType.USER, identity.userId().value(), identity.tenantId());
        return session;
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
