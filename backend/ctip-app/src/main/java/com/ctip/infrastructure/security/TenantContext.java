package com.ctip.infrastructure.security;

import com.ctip.domain.shared.Visibility;
import com.ctip.domain.tenant.TenantId;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * 請求範圍的租戶上下文(docs/spec/01-architecture.md §1.11),由 security filter 設定。
 * 禁止在 controller 手動傳 tenantId 過濾;查詢一律取 {@link #visibility()}。
 */
@Component
@RequestScope
public class TenantContext {

    private TenantId tenantId = TenantId.PUBLIC;
    private AuthState authState = AuthState.ANONYMOUS;

    public void bindAnonymous() {
        this.tenantId = TenantId.PUBLIC;
        this.authState = AuthState.ANONYMOUS;
    }

    /** M2 的認證流程綁定已驗證身分;M1 僅供測試與擴充點。 */
    public void bindAuthenticated(TenantId tenant) {
        this.tenantId = Objects.requireNonNull(tenant);
        this.authState = AuthState.AUTHENTICATED;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public AuthState authState() {
        return authState;
    }

    /** 對應 07 §7.7 可見度表:ANONYMOUS → public CLEAR;AUTHENTICATED → public CLEAR+GREEN + 自家全部。 */
    public Visibility visibility() {
        return authState == AuthState.ANONYMOUS ? Visibility.anonymous() : Visibility.authenticated(tenantId);
    }
}
