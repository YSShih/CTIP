package com.ctip.application.identity;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.PasswordHasherPort;
import com.ctip.application.port.UserRepository;
import com.ctip.domain.user.EmailAddress;
import com.ctip.domain.user.User;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 密碼認證與不變量 U7(連續失敗鎖定)。
 * 失敗以 {@link LoginResult} 回傳而非丟例外,確保失敗計數隨本交易提交(見 LoginResult)。
 */
@Service
public class LoginAuthenticator {

    private final UserRepository users;
    private final PasswordHasherPort passwordHasher;
    private final ClockPort clock;
    private final LoginPolicy policy;

    public LoginAuthenticator(
            UserRepository users, PasswordHasherPort passwordHasher, ClockPort clock, LoginPolicy policy) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
        this.policy = policy;
    }

    @Transactional
    public LoginResult authenticate(AuthCommands.Login command) {
        Optional<User> found = users.findByEmail(EmailAddress.of(command.email()));
        if (found.isEmpty()) {
            return LoginResult.failed(LoginFailure.INVALID_CREDENTIALS);
        }
        User user = found.get();
        Instant now = clock.now();
        if (user.isLocked(now)) {
            return LoginResult.failed(LoginFailure.LOCKED);
        }
        if (!user.isActive() || !passwordHasher.matches(command.password(), user.passwordHash())) {
            user.recordFailedLogin(now, policy.maxFailedAttempts(), policy.lockDuration());
            users.save(user);
            return LoginResult.failed(LoginFailure.INVALID_CREDENTIALS);
        }
        user.recordSuccessfulLogin(now);
        return LoginResult.success(users.save(user));
    }
}
