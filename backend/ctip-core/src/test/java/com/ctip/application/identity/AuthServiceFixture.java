package com.ctip.application.identity;

import com.ctip.testing.FakeAccessTokens;
import com.ctip.testing.FakePasswordHasher;
import com.ctip.testing.FixedClockPort;
import com.ctip.testing.InMemoryRefreshTokenRepository;
import com.ctip.testing.InMemoryTenantMemberships;
import com.ctip.testing.InMemoryTenantRepository;
import com.ctip.testing.InMemoryUserRepository;
import com.ctip.testing.RecordingEventPublisher;
import com.ctip.testing.SequentialIdGenerator;
import com.ctip.testing.SequentialTokenGenerator;
import com.ctip.testing.StubRolePermissions;
import java.time.Duration;
import java.time.Instant;

/** 以 in-memory port 組裝完整的認證流程,供 core 層單元測試使用(不啟動 Spring)。 */
final class AuthServiceFixture {

    static final String PASSWORD = "unit-test-password";
    static final Duration FAMILY_MAX_LIFETIME = Duration.ofDays(90);

    final InMemoryUserRepository users = new InMemoryUserRepository();
    final InMemoryRefreshTokenRepository refreshTokens = new InMemoryRefreshTokenRepository();
    final InMemoryTenantRepository tenants = new InMemoryTenantRepository();
    final InMemoryTenantMemberships memberships = new InMemoryTenantMemberships();
    final StubRolePermissions rolePermissions = new StubRolePermissions();
    final RecordingEventPublisher events = new RecordingEventPublisher();
    final FakeAccessTokens accessTokens = new FakeAccessTokens();
    final AccountAccessPolicy accounts = new AccountAccessPolicy(users, memberships);
    final IdentityResolver identityResolver = new IdentityResolver(accounts, rolePermissions);

    private final MutableClock clock;
    final AuthService authService;
    final RefreshTokenRotator rotator;
    final FakePasswordHasher passwordHasher = new FakePasswordHasher();

    AuthServiceFixture() {
        this(FixedClockPort.DEFAULT_NOW);
    }

    AuthServiceFixture(Instant start) {
        this.clock = new MutableClock(start);
        SequentialIdGenerator ids = new SequentialIdGenerator();
        RefreshTokenFactory tokenFactory = new RefreshTokenFactory(
                new SequentialTokenGenerator(),
                ids,
                clock,
                new RefreshTokenSettings(Duration.ofDays(30), FAMILY_MAX_LIFETIME));
        SessionIssuer sessionIssuer = new SessionIssuer(accessTokens, tokenFactory, refreshTokens, ids);
        UserRegistrar registrar = new UserRegistrar(
                users, new TenantProvisioner(tenants, memberships, ids, events), passwordHasher, ids, events);
        LoginAuthenticator loginAuthenticator =
                new LoginAuthenticator(users, passwordHasher, clock, new LoginPolicy(10, Duration.ofMinutes(15)));
        this.rotator = new RefreshTokenRotator(accounts, refreshTokens, tokenFactory, clock, events);
        this.authService = new AuthService(registrar, loginAuthenticator, rotator, sessionIssuer, identityResolver);
    }

    AuthSession register(String email) {
        return authService.register(
                new AuthCommands.Register(email, PASSWORD, "Unit Tester", "Unit Org"), ClientInfo.unknown());
    }

    AuthSession login(String email, String password) {
        return authService.login(new AuthCommands.Login(email, password, "junit", "127.0.0.1"));
    }

    AuthSession refresh(String refreshToken) {
        return authService.refresh(new AuthCommands.Refresh(refreshToken, "junit", "127.0.0.1"));
    }

    void advance(Duration amount) {
        clock.advance(amount);
    }

    /** 可前進的時鐘:登入鎖定過期等時間相關行為必須可控(§14.7 禁止 Instant.now())。 */
    private static final class MutableClock implements com.ctip.application.port.ClockPort {
        private Instant current;

        private MutableClock(Instant start) {
            this.current = start;
        }

        void advance(Duration amount) {
            current = current.plus(amount);
        }

        @Override
        public Instant now() {
            return current;
        }
    }
}
