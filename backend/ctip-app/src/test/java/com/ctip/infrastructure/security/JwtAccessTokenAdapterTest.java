package com.ctip.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.application.port.AccessTokenClaims;
import com.ctip.application.port.AccessTokenVerification;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.UserId;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * HS256 簽發與驗證(§10.4)。重點在<strong>否定面</strong>:被竄改演算法的 token 必須一律無效。
 *
 * <p>Phase 13 收尾稽核查核了 algorithm confusion —— {@code verify} 沒有自己檢查 header 的
 * {@code alg},安全性是靠 Nimbus(JWSHeader 解析即拒絕 {@code alg:none}、MACVerifier 對非 HMAC
 * 演算法丟例外)。結論是無漏洞,但那是<em>相依函式庫的行為</em>,升版可能改變,故以測試釘住(ADR 0013)。
 */
@Tag("unit")
class JwtAccessTokenAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-27T08:00:00Z");
    private static final String SECRET = "unit-test-only-secret-0123456789abcdef";

    private final JwtAccessTokenAdapter adapter = new JwtAccessTokenAdapter(SECRET, Duration.ofSeconds(900), () -> NOW);

    @Test
    void issuedTokenVerifiesAndCarriesOnlyTheSpecifiedClaims() {
        String token = adapter.issue(claims());
        AccessTokenVerification verification = adapter.verify(token);

        assertThat(verification.status()).isEqualTo(AccessTokenVerification.Status.VALID);
        assertThat(verification.claims().permissions()).containsExactly("ioc:read");
        assertThat(payloadOf(token))
                .as("§10.4:不放 email、姓名或任何個資")
                .doesNotContain("email")
                .doesNotContain("@");
    }

    @Test
    void expiredTokenIsDistinguishableFromAnInvalidOne() {
        JwtAccessTokenAdapter shortLived = new JwtAccessTokenAdapter(SECRET, Duration.ofSeconds(1), () -> NOW);
        String token = shortLived.issue(claims());

        JwtAccessTokenAdapter later =
                new JwtAccessTokenAdapter(SECRET, Duration.ofSeconds(1), () -> NOW.plusSeconds(60));
        assertThat(later.verify(token).status()).isEqualTo(AccessTokenVerification.Status.EXPIRED);
    }

    /** header 改成 {@code alg:none} 並拿掉簽章 —— 最典型的 algorithm confusion。 */
    @Test
    void algorithmNoneIsRejected() {
        String token = adapter.issue(claims());
        String[] parts = token.split("\\.");
        String forged = encode("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "." + parts[1] + ".";

        assertThat(adapter.verify(forged).status()).isEqualTo(AccessTokenVerification.Status.INVALID);
    }

    /** header 宣稱 RS256 但簽章是 HMAC 產物:非 HMAC 演算法一律不得通過 MAC 驗章。 */
    @Test
    void nonHmacAlgorithmHeaderIsRejected() {
        String[] parts = adapter.issue(claims()).split("\\.");
        String forged = encode("{\"alg\":\"RS256\",\"typ\":\"JWT\"}") + "." + parts[1] + "." + parts[2];

        assertThat(adapter.verify(forged).status()).isEqualTo(AccessTokenVerification.Status.INVALID);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String foreign = new JwtAccessTokenAdapter(
                        "another-unit-test-secret-0123456789abcdef", Duration.ofSeconds(900), () -> NOW)
                .issue(claims());

        assertThat(adapter.verify(foreign).status()).isEqualTo(AccessTokenVerification.Status.INVALID);
        assertThat(adapter.verify("not-a-jwt").status()).isEqualTo(AccessTokenVerification.Status.INVALID);
    }

    /** §10.4:JWT_SECRET 長度不足直接拒絕建構(HS256 要求 >= 32 bytes)。 */
    @Test
    void shortSecretIsRejectedAtConstruction() {
        assertThatThrownBy(() -> new JwtAccessTokenAdapter("too-short", Duration.ofSeconds(900), () -> NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    private static AccessTokenClaims claims() {
        return new AccessTokenClaims(
                new UserId(new UUID(0, 51)),
                new TenantId(new UUID(0, 52)),
                Set.of("USER"),
                Set.of("ioc:read"),
                new UUID(0, 53));
    }

    private static String payloadOf(String token) {
        return new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
    }

    private static String encode(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
