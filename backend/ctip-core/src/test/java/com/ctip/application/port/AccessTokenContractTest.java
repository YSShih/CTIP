package com.ctip.application.port;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.UserId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Access token port 的資料契約(docs/spec/10-identity-plans.md §10.4)。 */
@Tag("unit")
class AccessTokenContractTest {

    @Test
    void claimsAreDefensivelyCopied() {
        Set<String> mutableRoles = new java.util.HashSet<>(Set.of("USER"));
        AccessTokenClaims claims = new AccessTokenClaims(
                new UserId(new UUID(0, 1)),
                new TenantId(new UUID(0, 2)),
                mutableRoles,
                Set.of("ioc:read"),
                UUID.randomUUID());
        mutableRoles.add("SYSTEM_ADMIN");
        assertThat(claims.roles()).containsExactly("USER");
    }

    /** EXPIRED 與 INVALID 必須可區分:§9.4 對兩者的錯誤碼不同(安全測試條號 4)。 */
    @Test
    void verificationDistinguishesExpiredFromInvalid() {
        assertThat(AccessTokenVerification.expired().status()).isEqualTo(AccessTokenVerification.Status.EXPIRED);
        assertThat(AccessTokenVerification.invalid().status()).isEqualTo(AccessTokenVerification.Status.INVALID);
        assertThat(AccessTokenVerification.expired().claims()).isNull();

        AccessTokenClaims claims = new AccessTokenClaims(
                new UserId(new UUID(0, 1)), new TenantId(new UUID(0, 2)), Set.of(), Set.of(), UUID.randomUUID());
        assertThat(AccessTokenVerification.valid(claims).claims()).isEqualTo(claims);
    }
}
