package com.ctip.domain.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.domain.event.ApiKeyEvents;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.UserId;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** ApiKey 聚合的七條不變量 K1–K7(docs/spec/02-ddd-model.md §2.3;§14.2 要求逐條覆蓋)。 */
@Tag("unit")
class ApiKeyAggregateTest {

    private static final Instant NOW = Instant.parse("2026-08-27T08:00:00Z");
    private static final TenantId TENANT = new TenantId(new UUID(0, 1));
    private static final UserId USER = new UserId(new UUID(0, 2));
    private static final String RANDOM_SEGMENT = "aB3xY9kQ7fLm2pR8sT4uV6wX0yZ1cD5e";
    private static final String FULL_KEY = "ctip_mvp_" + RANDOM_SEGMENT;
    private static final Set<String> SYSTEM_PERMISSIONS = Set.of("ioc:read", "ioc:export", "ioc:submit");

    private static ApiKeySnapshot snapshot(Set<String> scopes, TenantId tenant, Instant expiresAt) {
        return new ApiKeySnapshot(
                new ApiKeyId(new UUID(0, 3)),
                tenant,
                USER,
                "ci-pipeline",
                ApiKeyFormat.prefixOf(FULL_KEY),
                KeyHash.of(FULL_KEY),
                new ScopeSet(scopes),
                expiresAt,
                null,
                null,
                NOW);
    }

    private static IssuedApiKey issue(Set<String> scopes, Set<String> granted) {
        return ApiKey.issue(snapshot(scopes, TENANT, null), FULL_KEY, SYSTEM_PERMISSIONS, granted);
    }

    @Test
    void k1KeyHashIsSha256OfTheFullKeyAndPlaintextIsReturnedOnce() {
        IssuedApiKey issued = issue(Set.of("ioc:read"), SYSTEM_PERMISSIONS);
        assertThat(issued.plaintext()).isEqualTo(FULL_KEY);
        assertThat(issued.apiKey().keyHash()).isEqualTo(KeyHash.of(FULL_KEY));
        // 聚合本身沒有任何取得原文的途徑
        assertThat(ApiKey.class.getMethods())
                .noneMatch(method ->
                        method.getName().toLowerCase(java.util.Locale.ROOT).contains("plaintext"));
    }

    @Test
    void k1MismatchedHashIsRejected() {
        ApiKeySnapshot tampered = new ApiKeySnapshot(
                new ApiKeyId(new UUID(0, 3)),
                TENANT,
                USER,
                "ci",
                ApiKeyFormat.prefixOf(FULL_KEY),
                KeyHash.of("ctip_mvp_" + "z".repeat(32)),
                new ScopeSet(Set.of("ioc:read")),
                null,
                null,
                null,
                NOW);
        assertThatThrownBy(() -> ApiKey.issue(tampered, FULL_KEY, SYSTEM_PERMISSIONS, SYSTEM_PERMISSIONS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("K1");
    }

    /**
     * K2:前綴取自<strong>隨機段</strong>前 8 碼。整串前 8 碼恆為 ctip_mvp 之類的環境常數,
     * 與 ux_api_keys_prefix 唯一約束衝突(ADR 0012 決策 2)。
     */
    @Test
    void k2PrefixComesFromTheRandomSegmentNotTheEnvelope() {
        assertThat(ApiKeyFormat.prefixOf(FULL_KEY).value()).isEqualTo(RANDOM_SEGMENT.substring(0, 8));
        assertThat(ApiKeyFormat.prefixOf(FULL_KEY).value()).isNotEqualTo("ctip_mvp");

        String other = "ctip_mvp_" + "Zz9Yy8Xx7Ww6Vv5Uu4Tt3Ss2Rr1Qq0P";
        assertThat(ApiKeyFormat.prefixOf(other + "z")).isNotEqualTo(ApiKeyFormat.prefixOf(FULL_KEY));
    }

    @Test
    void k2MalformedKeysAreRejected() {
        assertThat(ApiKeyFormat.isWellFormed("ctip_bad_" + RANDOM_SEGMENT)).isFalse();
        assertThat(ApiKeyFormat.isWellFormed("ctip_mvp_short")).isFalse();
        assertThat(ApiKeyFormat.isWellFormed(FULL_KEY)).isTrue();
        assertThat(ApiKeyFormat.compose("stg", RANDOM_SEGMENT)).isEqualTo("ctip_stg_" + RANDOM_SEGMENT);
    }

    /** 比對憑證一律走常數時間;{@code equals} 會在第一個相異字元短路,不得用於 secret。 */
    @Test
    void k1HashComparisonIsConstantTimeAndStillCorrect() {
        KeyHash stored = KeyHash.of(FULL_KEY);
        assertThat(stored.matches(FULL_KEY)).isTrue();
        assertThat(stored.matches("ctip_mvp_" + "z".repeat(32))).isFalse();
        // 只差最後一個字元也必須判否(常數時間不代表比對變寬鬆)
        assertThat(stored.matches(FULL_KEY.substring(0, FULL_KEY.length() - 1) + "f"))
                .isFalse();
    }

    @Test
    void k3ScopeOutsideTheSystemPermissionCatalogIsRejected() {
        assertThatThrownBy(() -> ApiKey.issue(
                        snapshot(Set.of("ioc:read"), TENANT, null), FULL_KEY, Set.of("ioc:export"), Set.of("ioc:read")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("K3");
    }

    @Test
    void k4ScopeMayNotExceedTheCreatorPermissions() {
        assertThatThrownBy(() -> issue(Set.of("ioc:submit"), Set.of("ioc:read", "ioc:export")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("K4");
        assertThat(issue(Set.of("ioc:read"), Set.of("ioc:read", "ioc:export"))
                        .apiKey()
                        .hasScope("ioc:read"))
                .isTrue();
    }

    @Test
    void k5PublicTenantMayNotOwnAnApiKey() {
        assertThatThrownBy(() -> ApiKey.issue(
                        snapshot(Set.of("ioc:read"), TenantId.PUBLIC, null),
                        FULL_KEY,
                        SYSTEM_PERMISSIONS,
                        SYSTEM_PERMISSIONS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("K5");
    }

    @Test
    void k6RevocationIsIrreversibleAndIdempotent() {
        ApiKey key = issue(Set.of("ioc:read"), SYSTEM_PERMISSIONS).apiKey();
        key.pullEvents();
        key.revoke(NOW);
        assertThat(key.revokedAt()).isEqualTo(NOW);
        assertThat(key.pullEvents()).singleElement().isInstanceOf(ApiKeyEvents.ApiKeyRevoked.class);

        key.revoke(NOW.plusSeconds(60));
        assertThat(key.revokedAt()).isEqualTo(NOW);
        assertThat(key.pullEvents()).isEmpty();
    }

    @Test
    void k7UsabilityRequiresNotRevokedAndNotExpired() {
        ApiKey never = issue(Set.of("ioc:read"), SYSTEM_PERMISSIONS).apiKey();
        assertThat(never.isUsable(NOW)).isTrue();

        ApiKey expiring = ApiKey.issue(
                        snapshot(Set.of("ioc:read"), TENANT, NOW.plusSeconds(60)),
                        FULL_KEY,
                        SYSTEM_PERMISSIONS,
                        SYSTEM_PERMISSIONS)
                .apiKey();
        assertThat(expiring.isUsable(NOW)).isTrue();
        assertThat(expiring.isUsable(NOW.plusSeconds(61))).isFalse();

        never.revoke(NOW);
        assertThat(never.isUsable(NOW)).isFalse();
    }

    @Test
    void issueRecordsApiKeyCreatedEvent() {
        ApiKey key = issue(Set.of("ioc:read"), SYSTEM_PERMISSIONS).apiKey();
        assertThat(key.pullEvents()).singleElement().isInstanceOf(ApiKeyEvents.ApiKeyCreated.class);
    }

    @Test
    void scopeFormatIsValidated() {
        assertThatThrownBy(() -> new ScopeSet(Set.of("NotAScope"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(new ScopeSet(Set.of("ioc:report-fp")).contains("ioc:report-fp"))
                .isTrue();
    }
}
