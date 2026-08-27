package com.ctip.domain.identity;

import com.ctip.domain.event.ApiKeyEvents;
import com.ctip.domain.event.DomainEvent;
import com.ctip.domain.event.PendingEvents;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * ApiKey 聚合根,不變量 K1–K7(docs/spec/02-ddd-model.md §2.3)。
 * K2 的全域唯一由 {@code ux_api_keys_prefix} 強制;其餘七條在本類別內強制。
 */
public final class ApiKey {

    private final PendingEvents pendingEvents = new PendingEvents();

    private final ApiKeyId id;
    private final TenantId tenantId;
    private final UserId userId;
    private final KeyPrefix keyPrefix;
    private final KeyHash keyHash;
    private final ScopeSet scopes;
    private final Instant expiresAt;
    private final Instant createdAt;
    private String name;
    private Instant lastUsedAt;
    private Instant revokedAt;

    private ApiKey(ApiKeySnapshot s) {
        this.id = Objects.requireNonNull(s.id(), "id 不得為 null");
        this.tenantId = Objects.requireNonNull(s.tenantId(), "tenantId 不得為 null");
        this.userId = Objects.requireNonNull(s.userId(), "userId 不得為 null");
        this.name = requireName(s.name());
        this.keyPrefix = Objects.requireNonNull(s.keyPrefix(), "keyPrefix 不得為 null");
        this.keyHash = Objects.requireNonNull(s.keyHash(), "keyHash 不得為 null");
        this.scopes = Objects.requireNonNull(s.scopes(), "scopes 不得為 null");
        this.expiresAt = s.expiresAt();
        this.lastUsedAt = s.lastUsedAt();
        this.revokedAt = s.revokedAt();
        this.createdAt = Objects.requireNonNull(s.createdAt(), "createdAt 不得為 null");
        if (tenantId.isPublic()) {
            throw new IllegalArgumentException("public tenant 不得有 API key(不變量 K5 / T3)");
        }
    }

    /**
     * 建立並回傳一次性原文(不變量 K1、K2)。scopes 必須同時是系統權限集合(K3)
     * 與建立者在該租戶所擁有權限(K4)的子集。
     */
    public static IssuedApiKey issue(
            ApiKeySnapshot snapshot, String plaintext, Set<String> systemPermissions, Set<String> granterPermissions) {
        if (snapshot.revokedAt() != null) {
            throw new IllegalArgumentException("新建立的 API key 不得已撤銷");
        }
        if (!snapshot.keyHash().equals(KeyHash.of(plaintext))) {
            throw new IllegalArgumentException("keyHash 必須為 SHA-256(fullKey)(不變量 K1)");
        }
        if (!snapshot.keyPrefix().equals(ApiKeyFormat.prefixOf(plaintext))) {
            throw new IllegalArgumentException("keyPrefix 必須取自完整 key 的隨機段前 8 碼(不變量 K2)");
        }
        if (!snapshot.scopes().isSubsetOf(systemPermissions)) {
            throw new IllegalArgumentException("scope 必須是系統定義權限的子集(不變量 K3)");
        }
        if (!snapshot.scopes().isSubsetOf(granterPermissions)) {
            throw new IllegalArgumentException("scope 不得超出建立者的權限(不變量 K4)");
        }
        ApiKey apiKey = new ApiKey(snapshot);
        apiKey.pendingEvents.record(new ApiKeyEvents.ApiKeyCreated(apiKey.tenantId, apiKey.id));
        return new IssuedApiKey(apiKey, plaintext);
    }

    /** 由持久化狀態重建(不重放事件,僅重新驗證不變量)。 */
    public static ApiKey reconstitute(ApiKeySnapshot snapshot) {
        return new ApiKey(snapshot);
    }

    /** 不變量 K6:一旦撤銷即不可清除,重複撤銷不改變時間也不重複發事件。 */
    public void revoke(Instant now) {
        if (revokedAt != null) {
            return;
        }
        this.revokedAt = Objects.requireNonNull(now, "now 不得為 null");
        pendingEvents.record(new ApiKeyEvents.ApiKeyRevoked(tenantId, id));
    }

    /** 不變量 K7。 */
    public boolean isUsable(Instant now) {
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }

    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }

    /** §10.5:last_used_at 非同步更新,容許最多 60 秒延遲。 */
    public void markUsed(Instant now) {
        this.lastUsedAt = Objects.requireNonNull(now, "now 不得為 null");
    }

    public void rename(String newName) {
        this.name = requireName(newName);
    }

    public List<DomainEvent> pullEvents() {
        return pendingEvents.pull();
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 不得為空");
        }
        return name;
    }

    public ApiKeyId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public UserId userId() {
        return userId;
    }

    public String name() {
        return name;
    }

    public KeyPrefix keyPrefix() {
        return keyPrefix;
    }

    public KeyHash keyHash() {
        return keyHash;
    }

    public ScopeSet scopes() {
        return scopes;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant lastUsedAt() {
        return lastUsedAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
