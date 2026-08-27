package com.ctip.infrastructure.security;

import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.domain.shared.Visibility;
import com.ctip.domain.tenant.TenantId;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * 請求範圍的租戶上下文(docs/spec/01-architecture.md §1.11),由 security filter 設定。
 * 禁止在 controller 手動傳 tenantId 過濾;查詢一律取 {@link #visibility()}。
 *
 * <p>Phase 13 起額外承載 {@link AuthenticatedIdentity}(userId / 角色 / 權限 / apiKeyId);
 * {@link AuthState} 維持兩態不變——它是 TLP 可見度的軸,Phase 13 明令不得改動 Phase 4
 * 建立的 TLP 過濾邏輯(ADR 0012 決策 3)。
 */
@Component
@RequestScope
public class TenantContext {

    private TenantId tenantId = TenantId.PUBLIC;
    private AuthState authState = AuthState.ANONYMOUS;
    private AuthenticatedIdentity identity;

    public void bindAnonymous() {
        this.tenantId = TenantId.PUBLIC;
        this.authState = AuthState.ANONYMOUS;
        this.identity = null;
    }

    /** 認證成功後綁定完整身分(JWT 或 API key 皆經此處)。 */
    public void bindAuthenticated(AuthenticatedIdentity authenticated) {
        this.identity = Objects.requireNonNull(authenticated, "identity 不得為 null");
        this.tenantId = authenticated.tenantId();
        this.authState = AuthState.AUTHENTICATED;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public AuthState authState() {
        return authState;
    }

    public Optional<AuthenticatedIdentity> identity() {
        return Optional.ofNullable(identity);
    }

    /** 已認證時必定有身分;供寫入端點取得 userId 等資訊。 */
    public AuthenticatedIdentity requireIdentity() {
        return identity().orElseThrow(() -> new IllegalStateException("此路徑要求已認證身分,但 TenantContext 為匿名"));
    }

    /** 對應 07 §7.7 可見度表:ANONYMOUS → public CLEAR;AUTHENTICATED → public CLEAR+GREEN + 自家全部。 */
    public Visibility visibility() {
        return authState == AuthState.ANONYMOUS ? Visibility.anonymous() : Visibility.authenticated(tenantId);
    }
}
