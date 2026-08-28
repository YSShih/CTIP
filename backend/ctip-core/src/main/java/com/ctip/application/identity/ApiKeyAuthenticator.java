package com.ctip.application.identity;

import com.ctip.application.port.ApiKeyRepository;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.RolePermissionRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.identity.ApiKey;
import com.ctip.domain.identity.ApiKeyFormat;
import com.ctip.domain.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API key 驗證(§10.5):以隨機段前 8 碼定位單一列,再比對 SHA-256——不做全表雜湊比對。
 * 有效權限為 {@code scopes ∩ 建立者當下角色的權限},使角色被降級時金鑰同步失效(不變量 K4 的執行期延伸)。
 *
 * <p>建立者被停權或已無該租戶的成員資格時,金鑰<strong>直接失效</strong>——
 * 不再退回預設 {@code USER} 角色(ADR 0013,見 {@link AccountAccessPolicy})。
 */
@Service
public class ApiKeyAuthenticator {

    /** §10.5:last_used_at 非同步更新,容許最多 60 秒延遲。 */
    private static final Duration LAST_USED_THROTTLE = Duration.ofSeconds(60);

    private final ApiKeyRepository apiKeys;
    private final AccountAccessPolicy accounts;
    private final RolePermissionRepository rolePermissions;
    private final ClockPort clock;

    public ApiKeyAuthenticator(
            ApiKeyRepository apiKeys,
            AccountAccessPolicy accounts,
            RolePermissionRepository rolePermissions,
            ClockPort clock) {
        this.apiKeys = apiKeys;
        this.accounts = accounts;
        this.rolePermissions = rolePermissions;
        this.clock = clock;
    }

    @Transactional
    public Optional<AuthenticatedIdentity> authenticate(String fullKey) {
        if (!ApiKeyFormat.isWellFormed(fullKey)) {
            return Optional.empty();
        }
        Instant now = clock.now();
        return apiKeys.findByPrefix(ApiKeyFormat.prefixOf(fullKey))
                .filter(key -> key.keyHash().matches(fullKey))
                .filter(key -> key.isUsable(now))
                .flatMap(key -> identityFor(key, now));
    }

    private Optional<AuthenticatedIdentity> identityFor(ApiKey key, Instant now) {
        Optional<User> owner = accounts.activeUser(key.userId());
        if (owner.isEmpty()) {
            return Optional.empty();
        }
        Optional<RoleCode> role = accounts.eligibleRole(owner.get(), key.tenantId());
        if (role.isEmpty()) {
            return Optional.empty();
        }
        touch(key, now);
        Set<String> effective = new LinkedHashSet<>(key.scopes().values());
        effective.retainAll(rolePermissions.permissionsOf(role.get()));
        return Optional.of(new AuthenticatedIdentity(key.userId(), key.tenantId(), role.get(), effective, key.id()));
    }

    private void touch(ApiKey key, Instant now) {
        Instant lastUsed = key.lastUsedAt();
        if (lastUsed != null && lastUsed.isAfter(now.minus(LAST_USED_THROTTLE))) {
            return;
        }
        key.markUsed(now);
        // 只寫 last_used_at 這一欄:整列覆寫會把併發撤銷寫入的 revoked_at 沖回 null
        apiKeys.markUsed(key.id(), now);
    }
}
