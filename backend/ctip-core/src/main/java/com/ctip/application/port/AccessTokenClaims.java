package com.ctip.application.port;

import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.UserId;
import java.util.Set;
import java.util.UUID;

/**
 * Access token 的 claims(docs/spec/10-identity-plans.md §10.4):
 * {@code sub}=userId、{@code tid}=tenantId、{@code roles}、{@code perms}、{@code jti}。
 * {@code iat}/{@code exp} 由簽發實作以 ClockPort 補上。
 * <strong>不得放 email、姓名或任何個資。</strong>
 */
public record AccessTokenClaims(
        UserId userId, TenantId tenantId, Set<String> roles, Set<String> permissions, UUID jti) {

    public AccessTokenClaims {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }
}
