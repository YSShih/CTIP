package com.ctip.domain.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.EmailAddress;
import com.ctip.domain.user.RawPassword;
import com.ctip.domain.user.UserId;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** identity 與 user 值物件的邊界條件(格式、長度、null)。 */
@Tag("unit")
class IdentityValueObjectTest {

    private static final String FULL_KEY = "ctip_mvp_aB3xY9kQ7fLm2pR8sT4uV6wX0yZ1cD5e";

    @Test
    void keyPrefixAndKeyHashRejectMalformedValues() {
        assertThatThrownBy(() -> new KeyPrefix("short")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KeyPrefix(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KeyHash("not-hex")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void apiKeyFormatRejectsNull() {
        assertThatThrownBy(() -> ApiKeyFormat.prefixOf(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(ApiKeyFormat.isWellFormed(null)).isFalse();
        assertThat(ApiKeyFormat.randomSegmentOf(FULL_KEY)).hasSize(ApiKeyFormat.RANDOM_SEGMENT_LENGTH);
    }

    @Test
    void emptyScopeSetIsAllowedAndReported() {
        ScopeSet empty = new ScopeSet(Set.of());
        assertThat(empty.isEmpty()).isTrue();
        assertThat(empty.isSubsetOf(Set.of("ioc:read"))).isTrue();
        assertThatThrownBy(() -> new ScopeSet(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstitutedKeyExposesItsDescriptiveFields() {
        Instant created = Instant.parse("2026-08-27T08:00:00Z");
        ApiKey key = ApiKey.reconstitute(new ApiKeySnapshot(
                new ApiKeyId(new UUID(0, 3)),
                new TenantId(new UUID(0, 1)),
                new UserId(new UUID(0, 2)),
                "ci-pipeline",
                ApiKeyFormat.prefixOf(FULL_KEY),
                KeyHash.of(FULL_KEY),
                new ScopeSet(Set.of("ioc:read")),
                created.plusSeconds(3600),
                created,
                null,
                created));
        assertThat(key.name()).isEqualTo("ci-pipeline");
        assertThat(key.expiresAt()).isEqualTo(created.plusSeconds(3600));
        assertThat(key.createdAt()).isEqualTo(created);
        assertThat(key.lastUsedAt()).isEqualTo(created);

        key.rename("renamed");
        assertThat(key.name()).isEqualTo("renamed");
        assertThatThrownBy(() -> key.rename(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rawPasswordAndEmailBoundaries() {
        assertThatThrownBy(() -> new RawPassword(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RawPassword("short")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RawPassword("x".repeat(257))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RawPassword(" ".repeat(12))).isInstanceOf(IllegalArgumentException.class);
        assertThat(new RawPassword("a".repeat(RawPassword.MIN_LENGTH)).value()).hasSize(RawPassword.MIN_LENGTH);

        assertThatThrownBy(() -> EmailAddress.of(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailAddress("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailAddress("a@b.c" + "d".repeat(320)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
