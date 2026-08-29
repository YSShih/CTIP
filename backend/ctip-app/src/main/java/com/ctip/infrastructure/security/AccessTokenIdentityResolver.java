package com.ctip.infrastructure.security;

import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.application.port.AccessTokenPort;
import com.ctip.application.port.AccessTokenVerification;
import com.ctip.application.rbac.RoleCode;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * access token → {@link AuthenticatedIdentity} 的唯一解析點。
 *
 * <p>REST({@link CtipAuthenticationFilter})與 WebSocket 握手(09 §9.1「即時推送」)兩條路徑
 * 共用它:JWT 的驗證與角色解析是安全判定,兩份實作遲早會分歧。
 */
public class AccessTokenIdentityResolver {

    private final AccessTokenPort accessTokens;

    public AccessTokenIdentityResolver(AccessTokenPort accessTokens) {
        this.accessTokens = accessTokens;
    }

    public Resolution resolve(String token) {
        AccessTokenVerification verification = accessTokens.verify(token);
        if (verification.status() != AccessTokenVerification.Status.VALID) {
            return new Resolution(verification.status(), null);
        }
        return roleOf(verification)
                .map(role -> new Resolution(
                        AccessTokenVerification.Status.VALID,
                        AuthenticatedIdentity.ofUser(
                                verification.claims().userId(),
                                verification.claims().tenantId(),
                                role,
                                verification.claims().permissions())))
                // 角色缺漏或無法辨識 = token 不是本系統簽的形狀,一律當作無效憑證(fail-closed)
                .orElseGet(() -> new Resolution(AccessTokenVerification.Status.INVALID, null));
    }

    /**
     * roles claim 是單元素陣列(一使用者在一租戶內恰一個角色,表 14 的 PK 保證)。
     */
    private static Optional<RoleCode> roleOf(AccessTokenVerification verification) {
        return verification.claims().roles().stream().findFirst().flatMap(AccessTokenIdentityResolver::parseRole);
    }

    private static Optional<RoleCode> parseRole(String code) {
        return Stream.of(RoleCode.values())
                .filter(role -> role.name().equals(code))
                .findFirst();
    }

    /** {@code identity} 僅在 {@code status == VALID} 時非 null。 */
    public record Resolution(AccessTokenVerification.Status status, AuthenticatedIdentity identity) {

        public boolean isValid() {
            return status == AccessTokenVerification.Status.VALID && identity != null;
        }
    }
}
