package com.ctip.application.identity;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.PasswordHasherPort;
import com.ctip.application.port.RefreshTokenRepository;
import com.ctip.application.port.UserRepository;
import com.ctip.domain.user.RawPassword;
import com.ctip.domain.user.RefreshToken;
import com.ctip.domain.user.RevokedReason;
import com.ctip.domain.user.User;
import com.ctip.domain.user.UserId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 變更密碼({@code POST /api/v1/auth/change-password})。
 *
 * <p><strong>改密碼必須撤銷該使用者的全部 refresh token family</strong>
 * ([ADR 0015] 指定的 M3 責任):否則「我覺得密碼外洩了,所以改密碼」這個動作
 * 完全擋不住已經握有 refresh token 的攻擊者——他可以繼續無限期輪替下去。
 *
 * <p>撤銷原因沿用 {@code ADMIN}:04 表 15 的列舉固定五值,為此新增一個值等於
 * 同時改 schema 與規格,而語意上「因帳號安全事件而由系統撤銷」正是該值涵蓋的情況。
 */
@Service
public class PasswordChangeService {

    private final UserRepository users;
    private final PasswordHasherPort passwordHasher;
    private final RefreshTokenRepository tokens;
    private final ClockPort clock;

    public PasswordChangeService(
            UserRepository users, PasswordHasherPort passwordHasher, RefreshTokenRepository tokens, ClockPort clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.tokens = tokens;
        this.clock = clock;
    }

    /**
     * @return 被撤銷的 refresh token 數量(呼叫端據此告知使用者「其他裝置已登出」)
     * @throws AuthenticationFailedException 目前密碼不符;訊息與登入失敗一致,不揭露差異
     */
    @Transactional
    public int change(UserId userId, String currentPassword, String newPassword) {
        User user = users.findById(userId).orElseThrow(() -> new AuthenticationFailedException("Invalid credentials"));
        if (!passwordHasher.matches(currentPassword, user.passwordHash())) {
            throw new AuthenticationFailedException("Invalid credentials");
        }
        RawPassword replacement = new RawPassword(newPassword);
        user.changePassword(passwordHasher.hash(replacement.value()));
        users.save(user);
        List<RefreshToken> active = tokens.findActiveByUser(userId);
        tokens.saveAll(user.revokeFamily(active, clock.now(), RevokedReason.ADMIN));
        return active.size();
    }
}
