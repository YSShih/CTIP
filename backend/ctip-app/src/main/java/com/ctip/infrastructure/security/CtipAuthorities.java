package com.ctip.infrastructure.security;

import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.application.rbac.RoleCode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * 權限 code 直接作為 Spring Security 的 authority——§10.3 的範例即
 * {@code @PreAuthorize("hasAuthority('ioc:export')")};角色另以 {@code ROLE_} 前綴提供。
 */
final class CtipAuthorities {

    private CtipAuthorities() {}

    static Collection<GrantedAuthority> of(AuthenticatedIdentity identity) {
        return of(identity.role(), identity.permissions());
    }

    static Collection<GrantedAuthority> of(RoleCode role, Set<String> permissions) {
        List<GrantedAuthority> authorities = new ArrayList<>(permissions.size() + 1);
        permissions.forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        return List.copyOf(authorities);
    }
}
