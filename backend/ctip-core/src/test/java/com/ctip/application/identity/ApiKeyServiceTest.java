package com.ctip.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.application.plan.PlanLimitExceededException;
import com.ctip.application.plan.QuotaService;
import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.event.ApiKeyEvents;
import com.ctip.domain.identity.ApiKeyFormat;
import com.ctip.domain.identity.IssuedApiKey;
import com.ctip.domain.identity.ScopeSet;
import com.ctip.domain.plan.BillingPeriod;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.plan.Subscription;
import com.ctip.domain.plan.SubscriptionId;
import com.ctip.domain.plan.SubscriptionProvider;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.EmailAddress;
import com.ctip.domain.user.PasswordHash;
import com.ctip.domain.user.User;
import com.ctip.domain.user.UserId;
import com.ctip.domain.user.UserSnapshot;
import com.ctip.domain.user.UserStatus;
import com.ctip.testing.CountingRateLimiter;
import com.ctip.testing.FixedClockPort;
import com.ctip.testing.InMemoryApiKeyRepository;
import com.ctip.testing.InMemoryPlanRepository;
import com.ctip.testing.InMemorySubscriptionRepository;
import com.ctip.testing.InMemoryTenantMemberships;
import com.ctip.testing.InMemoryUserRepository;
import com.ctip.testing.PlanFixtures;
import com.ctip.testing.RecordingEventPublisher;
import com.ctip.testing.SequentialIdGenerator;
import com.ctip.testing.SequentialTokenGenerator;
import com.ctip.testing.StubRolePermissions;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** API key 的建立、撤銷與驗證(docs/spec/10-identity-plans.md §10.5;不變量 K1–K7 的服務層)。 */
@Tag("unit")
class ApiKeyServiceTest {

    private static final TenantId TENANT = new TenantId(new UUID(0, 11));
    private static final TenantId OTHER_TENANT = new TenantId(new UUID(0, 12));
    private static final UserId USER = new UserId(new UUID(0, 21));
    private static final int MAX_KEYS_PER_TENANT = 10;

    private final InMemoryApiKeyRepository apiKeyRepository = new InMemoryApiKeyRepository();
    private final StubRolePermissions rolePermissions = new StubRolePermissions();
    private final InMemoryTenantMemberships memberships = new InMemoryTenantMemberships();
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final RecordingEventPublisher events = new RecordingEventPublisher();
    private final FixedClockPort clock = FixedClockPort.at(FixedClockPort.DEFAULT_NOW);

    private ApiKeyService service;
    private ApiKeyAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        ApiKeySettings settings = new ApiKeySettings("mvp");
        ApiKeyFactory factory = new ApiKeyFactory(
                new SequentialTokenGenerator(), new SequentialIdGenerator(), clock, settings, rolePermissions);
        // 數量上限自 Phase 14 起讀 plans.max_api_keys;PREMIUM 為 10(§10.6)
        QuotaService quotas = new QuotaService(
                new InMemoryPlanRepository(), premiumSubscription(), new CountingRateLimiter(clock), clock);
        service = new ApiKeyService(apiKeyRepository, quotas, factory, events, clock);
        users.save(owner(UserStatus.ACTIVE));
        authenticator = new ApiKeyAuthenticator(
                apiKeyRepository, new AccountAccessPolicy(users, memberships), rolePermissions, clock);
        memberships.assign(TENANT, USER, RoleCode.TENANT_ADMIN);
    }

    /** 建立者被停權 → 金鑰立即失效。金鑰自己沒過期,但持有者已不該有存取權(ADR 0013)。 */
    @Test
    void suspendedOwnerInvalidatesTheKey() {
        IssuedApiKey issued = issue("suspend-me", Set.of("ioc:read"), RoleCode.TENANT_ADMIN);
        assertThat(authenticator.authenticate(issued.plaintext())).isPresent();

        users.save(owner(UserStatus.SUSPENDED));

        assertThat(authenticator.authenticate(issued.plaintext())).isEmpty();
    }

    /** 成員資格被移除 → 金鑰失效,而不是靜默降級成 USER 角色。 */
    @Test
    void removedMembershipInvalidatesTheKeyInsteadOfDowngradingIt() {
        IssuedApiKey issued = issue("orphan", Set.of("ioc:read"), RoleCode.TENANT_ADMIN);
        assertThat(authenticator.authenticate(issued.plaintext())).isPresent();

        memberships.remove(TENANT, USER);

        assertThat(authenticator.authenticate(issued.plaintext())).isEmpty();
    }

    /** 測試租戶固定為 PREMIUM(max_api_keys = 10),與 MAX_KEYS_PER_TENANT 對齊。 */
    private InMemorySubscriptionRepository premiumSubscription() {
        InMemorySubscriptionRepository subscriptions = new InMemorySubscriptionRepository();
        subscriptions.save(Subscription.subscribe(
                new SubscriptionId(java.util.UUID.nameUUIDFromBytes("sub".getBytes(StandardCharsets.UTF_8))),
                TENANT,
                PlanFixtures.of(PlanCode.PREMIUM),
                SubscriptionProvider.MANUAL,
                BillingPeriod.openEnded(FixedClockPort.DEFAULT_NOW)));
        return subscriptions;
    }

    /** §10.5 的每租戶數量上限;countActive 原本是無呼叫端的死程式。 */
    @Test
    void quotaIsEnforcedPerTenant() {
        for (int i = 0; i < MAX_KEYS_PER_TENANT; i++) {
            issue("key-" + i, Set.of("ioc:read"), RoleCode.TENANT_ADMIN);
        }
        assertThatThrownBy(() -> issue("one-too-many", Set.of("ioc:read"), RoleCode.TENANT_ADMIN))
                .isInstanceOf(PlanLimitExceededException.class);
    }

    /** 金鑰的建立者。API key 驗證會查它的狀態與成員資格(ADR 0013 fail-closed)。 */
    private static User owner(UserStatus status) {
        return User.reconstitute(new UserSnapshot(
                USER,
                EmailAddress.of("owner@example.org"),
                new PasswordHash("$2a$12$0123456789012345678901234567890123456789012345678901"),
                "Key Owner",
                status,
                TENANT,
                null,
                0,
                null));
    }

    private AuthenticatedIdentity creator(RoleCode role) {
        return AuthenticatedIdentity.ofUser(USER, TENANT, role, rolePermissions.permissionsOf(role));
    }

    private IssuedApiKey issue(String name, Set<String> scopes, RoleCode role) {
        return service.issue(new ApiKeyIssueRequest(TENANT, USER, name, new ScopeSet(scopes), null), creator(role));
    }

    @Test
    void issuedKeyFollowsTheDocumentedFormatAndPublishesAnEvent() {
        IssuedApiKey issued = issue("ci", Set.of("ioc:read"), RoleCode.TENANT_ADMIN);
        assertThat(issued.plaintext()).matches("^ctip_mvp_[0-9A-Za-z]{32}$");
        assertThat(issued.apiKey().keyPrefix()).isEqualTo(ApiKeyFormat.prefixOf(issued.plaintext()));
        assertThat(events.published()).hasAtLeastOneElementOfType(ApiKeyEvents.ApiKeyCreated.class);
        assertThat(apiKeyRepository.countActive(TENANT)).isEqualTo(1);
    }

    @Test
    void scopeMayNotExceedTheCreatorPermissions() {
        assertThatThrownBy(() -> issue("escalate", Set.of("ioc:publish"), RoleCode.USER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void issuingForAnotherTenantIsRejected() {
        AuthenticatedIdentity foreign =
                AuthenticatedIdentity.ofUser(USER, OTHER_TENANT, RoleCode.TENANT_ADMIN, Set.of("ioc:read"));
        assertThatThrownBy(() -> service.issue(
                        new ApiKeyIssueRequest(TENANT, USER, "x", new ScopeSet(Set.of("ioc:read")), null), foreign))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revocationIsScopedToTheOwningTenant() {
        IssuedApiKey issued = issue("ci", Set.of("ioc:read"), RoleCode.TENANT_ADMIN);
        assertThatThrownBy(() -> service.revoke(issued.apiKey().id(), OTHER_TENANT))
                .isInstanceOf(ApiKeyNotFoundException.class);

        service.revoke(issued.apiKey().id(), TENANT);
        assertThat(apiKeyRepository.findById(issued.apiKey().id()).orElseThrow().revokedAt())
                .isNotNull();
        assertThat(events.published()).hasAtLeastOneElementOfType(ApiKeyEvents.ApiKeyRevoked.class);
        assertThat(service.countActive(TENANT)).isZero();
    }

    @Test
    void listingReturnsTheTenantsKeys() {
        issue("one", Set.of("ioc:read"), RoleCode.TENANT_ADMIN);
        issue("two", Set.of("ioc:export"), RoleCode.TENANT_ADMIN);
        assertThat(service.list(TENANT)).hasSize(2);
        assertThat(service.list(OTHER_TENANT)).isEmpty();
    }

    @Test
    void authenticationResolvesEffectivePermissionsAsScopesIntersectRolePermissions() {
        IssuedApiKey issued = issue("ci", Set.of("ioc:read", "ioc:export"), RoleCode.TENANT_ADMIN);
        AuthenticatedIdentity identity =
                authenticator.authenticate(issued.plaintext()).orElseThrow();

        assertThat(identity.isApiKey()).isTrue();
        assertThat(identity.apiKeyId()).isEqualTo(issued.apiKey().id());
        assertThat(identity.permissions()).containsExactlyInAnyOrder("ioc:read", "ioc:export");

        // 建立者被降級後,金鑰的有效權限同步縮小
        memberships.assign(TENANT, USER, RoleCode.ANONYMOUS);
        assertThat(authenticator.authenticate(issued.plaintext()).orElseThrow().permissions())
                .containsExactly("ioc:read");
    }

    @Test
    void malformedRevokedAndExpiredKeysDoNotAuthenticate() {
        assertThat(authenticator.authenticate("not-a-key")).isEmpty();
        assertThat(authenticator.authenticate(null)).isEmpty();

        IssuedApiKey issued = issue("ci", Set.of("ioc:read"), RoleCode.TENANT_ADMIN);
        assertThat(authenticator.authenticate("ctip_mvp_" + "z".repeat(32))).isEmpty();

        service.revoke(issued.apiKey().id(), TENANT);
        assertThat(authenticator.authenticate(issued.plaintext())).isEmpty();
    }

    @Test
    void lastUsedIsUpdatedAtMostOncePerThrottleWindow() {
        IssuedApiKey issued = issue("ci", Set.of("ioc:read"), RoleCode.TENANT_ADMIN);
        authenticator.authenticate(issued.plaintext());
        var afterFirst =
                apiKeyRepository.findById(issued.apiKey().id()).orElseThrow().lastUsedAt();
        assertThat(afterFirst).isEqualTo(FixedClockPort.DEFAULT_NOW);

        authenticator.authenticate(issued.plaintext());
        assertThat(apiKeyRepository.findById(issued.apiKey().id()).orElseThrow().lastUsedAt())
                .isEqualTo(afterFirst);
    }

    @Test
    void environmentSegmentIsValidated() {
        assertThatThrownBy(() -> new ApiKeySettings("production")).isInstanceOf(IllegalArgumentException.class);
        assertThat(new ApiKeySettings("prod").environment()).isEqualTo("prod");
    }

    @Test
    void refreshTokenSettingsRejectNonPositiveTtl() {
        assertThatThrownBy(() -> new RefreshTokenSettings(Duration.ZERO, Duration.ofDays(90)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RefreshTokenSettings(Duration.ofDays(30), Duration.ofDays(7)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginPolicy(0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginPolicy(10, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }
}
