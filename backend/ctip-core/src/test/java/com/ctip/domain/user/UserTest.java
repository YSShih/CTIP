package com.ctip.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.domain.event.UserEvents;
import com.ctip.domain.tenant.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** User 聚合的七條不變量 U1–U7(docs/spec/02-ddd-model.md §2.3;§14.2 要求逐條覆蓋)。 */
@Tag("unit")
class UserTest {

    private static final Instant NOW = Instant.parse("2026-08-27T08:00:00Z");
    private static final String BCRYPT_12 = "$2a$12$abcdefghijklmnopqrstuuKq1F9L1hE8OJ0hFm8dxOFVXzB4rNKa";
    private static final TenantId TENANT = new TenantId(new UUID(0, 1));
    private static final UserId USER = new UserId(new UUID(0, 2));

    private static UserSnapshot snapshot() {
        return new UserSnapshot(
                USER,
                new EmailAddress("analyst@example.org"),
                new PasswordHash(BCRYPT_12),
                "Alice",
                UserStatus.ACTIVE,
                TENANT,
                null,
                0,
                null);
    }

    private static User user() {
        return User.reconstitute(snapshot());
    }

    @Nested
    class U1EmailIsLowercase {

        @Test
        void uppercaseEmailIsRejected() {
            assertThatThrownBy(() -> new EmailAddress("Analyst@Example.org"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("U1");
        }

        @Test
        void factoryNormalisesInput() {
            assertThat(EmailAddress.of("  Analyst@Example.ORG ").value()).isEqualTo("analyst@example.org");
        }

        @Test
        void malformedEmailIsRejected() {
            assertThatThrownBy(() -> EmailAddress.of("not-an-email")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class U2PrimaryTenantIsNotPublic {

        @Test
        void publicTenantCannotOwnAUser() {
            UserSnapshot invalid = new UserSnapshot(
                    USER,
                    new EmailAddress("analyst@example.org"),
                    new PasswordHash(BCRYPT_12),
                    "Alice",
                    UserStatus.ACTIVE,
                    TenantId.PUBLIC,
                    null,
                    0,
                    null);
            assertThatThrownBy(() -> User.reconstitute(invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("U2");
        }
    }

    @Nested
    class U3PasswordIsNeverPlaintext {

        @Test
        void plaintextIsRejected() {
            assertThatThrownBy(() -> new PasswordHash("correct-horse-battery"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("U3");
        }

        @Test
        void bcryptBelowCostTwelveIsRejected() {
            assertThatThrownBy(() -> new PasswordHash("$2a$10$abcdefghijklmnopqrstuu"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("U3");
        }

        @Test
        void argon2idIsAccepted() {
            assertThat(new PasswordHash("$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA").value())
                    .startsWith("$argon2id$");
        }

        @Test
        void rawPasswordNeverLeaksThroughToString() {
            assertThat(new RawPassword("correct-horse-battery").toString()).doesNotContain("correct-horse");
        }
    }

    @Test
    void registrationRecordsUserRegisteredEvent() {
        User registered = User.register(snapshot());
        List<?> events = registered.pullEvents();
        assertThat(events).singleElement().isInstanceOf(UserEvents.UserRegistered.class);
        assertThat(registered.pullEvents()).isEmpty();
    }

    @Test
    void u7LockoutAfterThresholdAndResetOnSuccess() {
        User user = user();
        for (int attempt = 0; attempt < 9; attempt++) {
            user.recordFailedLogin(NOW, 10, Duration.ofMinutes(15));
        }
        assertThat(user.isLocked(NOW)).isFalse();

        user.recordFailedLogin(NOW, 10, Duration.ofMinutes(15));
        assertThat(user.failedLoginCount()).isEqualTo(10);
        assertThat(user.isLocked(NOW)).isTrue();
        assertThat(user.isLocked(NOW.plus(Duration.ofMinutes(16)))).isFalse();

        user.recordSuccessfulLogin(NOW);
        assertThat(user.failedLoginCount()).isZero();
        assertThat(user.lockedUntil()).isNull();
        assertThat(user.lastLoginAt()).isEqualTo(NOW);
    }

    @Test
    void changePasswordReplacesTheHash() {
        User user = user();
        String other = "$2b$12$zyxwvutsrqponmlkjihgfeKq1F9L1hE8OJ0hFm8dxOFVXzB4rNKa";
        user.changePassword(new PasswordHash(other));
        assertThat(user.passwordHash().value()).isEqualTo(other);
    }
}
