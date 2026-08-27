package com.ctip.infrastructure.security;

import com.ctip.application.identity.AuthenticatedIdentity;
import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * 已解析的身分在 Spring Security 中的載體。principal 為 {@link AuthenticatedIdentity},
 * 供集中的 {@link CtipPermissionEvaluator} 做 tenant-scoped 判斷。
 *
 * <p>匿名請求同樣以本型別承載(role = ANONYMOUS、權限取自 roles 表),
 * 因此 @PreAuthorize 不通過時是「權限不足 403」而非「未認證 401」——匿名是 §10.2 承認的
 * 正當身分,並非缺少憑證(ADR 0012 決策 8)。
 */
public class CtipAuthenticationToken extends AbstractAuthenticationToken {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final transient AuthenticatedIdentity identity;
    private final boolean authenticatedUser;

    CtipAuthenticationToken(
            AuthenticatedIdentity identity, Collection<GrantedAuthority> authorities, boolean authenticatedUser) {
        super(authorities);
        this.identity = identity;
        this.authenticatedUser = authenticatedUser;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return identity;
    }

    @Override
    public String getName() {
        return identity == null ? "anonymous" : identity.userId().value().toString();
    }

    /** 是否為登入使用者(相對於匿名)。 */
    public boolean isAuthenticatedUser() {
        return authenticatedUser;
    }
}
