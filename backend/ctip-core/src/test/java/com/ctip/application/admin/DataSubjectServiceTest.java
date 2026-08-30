package com.ctip.application.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.application.audit.AuditActorSummary;
import com.ctip.application.audit.AuditLogQuery;
import com.ctip.application.audit.AuditRecord;
import com.ctip.application.port.AuditLogPort;
import com.ctip.domain.shared.CursorPage;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.EmailAddress;
import com.ctip.domain.user.PasswordHash;
import com.ctip.domain.user.User;
import com.ctip.domain.user.UserId;
import com.ctip.domain.user.UserSnapshot;
import com.ctip.domain.user.UserStatus;
import com.ctip.testing.InMemoryRefreshTokenRepository;
import com.ctip.testing.InMemoryUserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 資料主體查詢與刪除(docs/spec/13-platform-ops.md §13.4)。
 *
 * <p>刪除的界線是重點:可識別欄位抹除、refresh token 整列刪除,而
 * <strong>稽核軌跡不動</strong>——它是 append-only 的,依保留政策到期(§13.5 規則 1)。
 */
@Tag("unit")
class DataSubjectServiceTest {

    private static final UserId USER_ID = new UserId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
    private static final TenantId TENANT = new TenantId(UUID.fromString("55555555-5555-5555-5555-555555555555"));

    private InMemoryUserRepository users;
    private DataSubjectService service;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        users.save(user());
        service = new DataSubjectService(users, new InMemoryRefreshTokenRepository(), new FixedAuditSummary());
    }

    @Test
    void theReportListsWhatThePlatformHoldsAboutTheSubject() {
        DataSubjectReport report = service.report(USER_ID);

        assertThat(report.email()).isEqualTo("subject@example.org");
        assertThat(report.status()).isEqualTo("ACTIVE");
        assertThat(report.auditTrail().count()).isEqualTo(7);
    }

    @Test
    void erasureReplacesTheIdentifyingFieldsAndSuspendsTheAccount() {
        DataSubjectErasure erasure = service.erase(USER_ID);

        User erased = users.findById(USER_ID).orElseThrow();
        assertThat(erased.email().value()).isEqualTo(USER_ID.value() + "@erased.invalid");
        assertThat(erased.displayName()).isNull();
        assertThat(erased.status()).isEqualTo(UserStatus.SUSPENDED);
        // 稽核列仍在:它們不由刪除操作處理,而是依保留政策到期
        assertThat(erasure.retainedAuditEntries().count()).isEqualTo(7);
    }

    @Test
    void anUnknownSubjectIsReportedAsNotFound() {
        assertThatThrownBy(() -> service.report(new UserId(UUID.randomUUID())))
                .isInstanceOf(AdminResourceNotFoundException.class);
    }

    private static User user() {
        return User.reconstitute(new UserSnapshot(
                USER_ID,
                EmailAddress.of("subject@example.org"),
                new PasswordHash("$2b$12$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123"),
                "Subject",
                UserStatus.ACTIVE,
                TENANT,
                null,
                0,
                null));
    }

    private static final class FixedAuditSummary implements AuditLogPort {

        @Override
        public void append(List<AuditRecord> records) {
            throw new UnsupportedOperationException("資料主體路徑不寫稽核表");
        }

        @Override
        public CursorPage<AuditRecord> list(AuditLogQuery query) {
            return CursorPage.lastPage(List.of());
        }

        @Override
        public AuditActorSummary summarizeActor(UUID actorId) {
            return new AuditActorSummary(
                    7, Instant.parse("2026-03-04T11:02:00Z"), Instant.parse("2026-08-30T09:15:04Z"));
        }
    }
}
