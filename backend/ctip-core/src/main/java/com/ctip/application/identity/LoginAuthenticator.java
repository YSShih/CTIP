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
        Instant now = clock.now();
        // 密碼比對「一律」執行,帳號不存在時比對 dummy hash:所有失敗路徑的耗時因此一致。
        // 若在此之前就 return,不存在的帳號會在數毫秒內回應、存在的要數百毫秒(BCrypt),
        // 回應時間本身即可列舉出哪些 email 已註冊——錯誤訊息一致也擋不住。
        boolean passwordMatches = passwordHasher.matches(
                command.password(), found.map(User::passwordHash).orElseGet(passwordHasher::dummyHash));
        if (found.isEmpty()) {
            return LoginResult.failed(LoginFailure.INVALID_CREDENTIALS);
        }
        User user = found.get();
        if (user.isLocked(now)) {
            return LoginResult.failed(LoginFailure.LOCKED);
        }
        if (!user.isActive() || !passwordMatches) {
            user.recordFailedLogin(now, policy.maxFailedAttempts(), policy.lockDuration());
            users.save(user);
            return LoginResult.failed(LoginFailure.INVALID_CREDENTIALS);
        }
        user.recordSuccessfulLogin(now);
        return LoginResult.success(users.save(user));
    }
}
