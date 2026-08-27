package com.ctip.application.identity;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.EventPublisherPort;
import com.ctip.application.port.RefreshTokenRepository;
import com.ctip.application.port.UserRepository;
import com.ctip.domain.user.RefreshToken;
import com.ctip.domain.user.RefreshTokenRotation;
import com.ctip.domain.user.RefreshTokenRotationCommand;
import com.ctip.domain.user.RefreshTokenRotationOutcome;
import com.ctip.domain.user.RevokedReason;
import com.ctip.domain.user.TokenHash;
import com.ctip.domain.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 單次使用 + 輪替 + 重用偵測(不變量 U4–U6;docs/spec/10-identity-plans.md §10.4)。
 * 重用時撤銷整個 family 並發 {@code TokenReuseDetected};已撤銷／已過期只拒絕不牽連 family。
 */
@Service
public class RefreshTokenRotator {

    private final UserRepository users;
    private final RefreshTokenRepository tokens;
    private final RefreshTokenFactory factory;
    private final ClockPort clock;
    private final EventPublisherPort events;

    public RefreshTokenRotator(
            UserRepository users,
            RefreshTokenRepository tokens,
            RefreshTokenFactory factory,
            ClockPort clock,
            EventPublisherPort events) {
        this.users = users;
        this.tokens = tokens;
        this.factory = factory;
        this.clock = clock;
        this.events = events;
    }

    @Transactional
    public RotatedTokens rotate(AuthCommands.Refresh command) {
        Optional<RefreshToken> located = locate(command.refreshToken());
        if (located.isEmpty()) {
            return RotatedTokens.failed(RefreshTokenRotationOutcome.INVALID);
        }
        RefreshToken presented = located.get();
        Optional<User> owner = users.findById(presented.userId());
        if (owner.isEmpty()) {
            return RotatedTokens.failed(RefreshTokenRotationOutcome.INVALID);
        }
        User user = owner.get();
        ClientInfo client = new ClientInfo(command.userAgent(), command.ip());
        IssuedRefreshToken replacement = factory.create(user.id(), presented.familyId(), presented.id(), client);
        RefreshTokenRotation rotation = user.rotateRefreshToken(new RefreshTokenRotationCommand(
                presented, tokens.findByFamily(presented.familyId()), replacement.token(), clock.now()));
        tokens.saveAll(rotation.mutated());
        if (rotation.isRotated()) {
            // 新枚必須與「舊枚被消耗」在同一個交易內提交:否則舊枚已作廢而新枚沒存進去,
            // 使用者會被無聲登出,且該 family 沒有任何可用的後繼
            tokens.save(replacement.token());
        }
        user.pullEvents().forEach(events::publish);
        return rotation.isRotated()
                ? RotatedTokens.rotated(user, replacement)
                : RotatedTokens.failed(rotation.outcome());
    }

    /** 登出:撤銷該枚所屬 family 的全部 token(§10.4 撤銷清單)。 */
    @Transactional
    public boolean revokeSession(String refreshToken) {
        Optional<RefreshToken> located = locate(refreshToken);
        if (located.isEmpty()) {
            return false;
        }
        RefreshToken presented = located.get();
        Optional<User> owner = users.findById(presented.userId());
        if (owner.isEmpty()) {
            return false;
        }
        List<RefreshToken> family = tokens.findByFamily(presented.familyId());
        tokens.saveAll(owner.get().revokeFamily(family, clock.now(), RevokedReason.LOGOUT));
        return true;
    }

    private Optional<RefreshToken> locate(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return Optional.empty();
        }
        return tokens.findByHash(TokenHash.of(plaintext));
    }
}
