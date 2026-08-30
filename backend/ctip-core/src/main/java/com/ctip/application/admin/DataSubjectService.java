package com.ctip.application.admin;

import com.ctip.application.port.AuditLogPort;
import com.ctip.application.port.RefreshTokenRepository;
import com.ctip.application.port.UserRepository;
import com.ctip.domain.user.EmailAddress;
import com.ctip.domain.user.RefreshToken;
import com.ctip.domain.user.User;
import com.ctip.domain.user.UserId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 資料主體的查詢與刪除(docs/spec/13-platform-ops.md §13.4:
 * 「提供資料主體查詢與刪除的操作程序(M3 提供管理端點)」)。
 *
 * <p>平台持有的可識別個資只有三處:{@code users} 的 email 與顯示名稱、
 * {@code refresh_tokens} 的 {@code ip}／{@code user_agent}、{@code audit_logs} 的
 * {@code ip}／{@code user_agent}。IOC 本身<strong>不主動關聯可識別自然人</strong>(§13.4)。
 *
 * <p><strong>稽核軌跡不在刪除範圍內</strong>:{@code audit_logs} 是 append-only 的
 * (§13.5 規則 1,DB 層強制),刪除權在此讓位給「網路與資訊安全」的正當利益與
 * 保存義務,並以 {@code AUDIT_RETENTION_DAYS}(180 天)的保留上限收斂。
 * 刪除後留在稽核列上的只有 {@code actor_id} 這個化名識別碼——它已經對應不到任何可識別欄位。
 * 詳見 {@code docs/deployment/privacy.md}。
 */
@Service
public class DataSubjectService {

    private static final String ERASED_EMAIL_DOMAIN = "@erased.invalid";

    private final UserRepository users;
    private final RefreshTokenRepository tokens;
    private final AuditLogPort auditLogs;

    public DataSubjectService(UserRepository users, RefreshTokenRepository tokens, AuditLogPort auditLogs) {
        this.users = users;
        this.tokens = tokens;
        this.auditLogs = auditLogs;
    }

    @Transactional(readOnly = true)
    public DataSubjectReport report(UserId userId) {
        User user = require(userId);
        List<RefreshToken> active = tokens.findActiveByUser(userId);
        return new DataSubjectReport(
                user.id().value(),
                user.email().value(),
                user.displayName(),
                user.status().name(),
                user.lastLoginAt(),
                active.size(),
                auditLogs.summarizeActor(userId.value()));
    }

    /**
     * 刪除:refresh token 整列刪除(ip / user_agent 是個資,撤銷只是讓 token 失效、列還在),
     * 使用者的可識別欄位以佔位值取代並停權。
     *
     * <p>不刪 {@code users} 整列:{@code tenant_users}、{@code api_keys} 等都以 FK 指向它,
     * 而那些列承載的是租戶的營運事實,不是這個人的個資。
     */
    @Transactional
    public DataSubjectErasure erase(UserId userId) {
        User user = require(userId);
        int deletedTokens = tokens.deleteByUser(userId);
        user.erasePersonalData(EmailAddress.of(userId.value() + ERASED_EMAIL_DOMAIN));
        users.save(user);
        return new DataSubjectErasure(userId.value(), deletedTokens, auditLogs.summarizeActor(userId.value()));
    }

    private User require(UserId userId) {
        return users.findById(userId)
                .orElseThrow(() -> new AdminResourceNotFoundException("No such user: " + userId.value()));
    }
}
