package com.ctip.infrastructure.persistence;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.RolePermissionRepository;
import com.ctip.application.rbac.RoleCode;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * RBAC 參考資料的讀取。角色→權限對應存於資料庫(V24 種入),此處不硬編任何一格。
 *
 * <p>匿名請求也要取得 ANONYMOUS 角色的權限,若每請求查一次 DB 會成為讀取路徑的固定成本;
 * 由於本表只由 migration 變更,以 60 秒 TTL 記憶化(仍保留 §10.3「可在資料庫調整」的語意,
 * 最多延遲一分鐘生效)。分散式快取為 Phase 17。
 */
@Repository
@Transactional(readOnly = true)
class RolePermissionRepositoryAdapter implements RolePermissionRepository {

    private static final Duration TTL = Duration.ofSeconds(60);
    private static final String ALL_PERMISSIONS = "*";

    private final RoleJpaRepository roles;
    private final PermissionJpaRepository permissions;
    private final ClockPort clock;
    private final Map<String, CachedPermissions> cache = new ConcurrentHashMap<>();

    RolePermissionRepositoryAdapter(RoleJpaRepository roles, PermissionJpaRepository permissions, ClockPort clock) {
        this.roles = roles;
        this.permissions = permissions;
        this.clock = clock;
    }

    @Override
    public Set<String> permissionsOf(RoleCode role) {
        return cached(
                role.name(),
                () -> roles.findByCode(role.name())
                        .map(entity -> entity.permissions.stream()
                                .map(permission -> permission.code)
                                .collect(Collectors.toUnmodifiableSet()))
                        .orElseGet(Set::of));
    }

    @Override
    public Set<String> allPermissionCodes() {
        return cached(
                ALL_PERMISSIONS,
                () -> permissions.findAll().stream()
                        .map(permission -> permission.code)
                        .collect(Collectors.toUnmodifiableSet()));
    }

    private Set<String> cached(String key, java.util.function.Supplier<Set<String>> loader) {
        Instant now = clock.now();
        CachedPermissions entry = cache.get(key);
        if (entry != null && entry.loadedAt().plus(TTL).isAfter(now)) {
            return entry.values();
        }
        Set<String> loaded = loader.get();
        cache.put(key, new CachedPermissions(loaded, now));
        return loaded;
    }

    private record CachedPermissions(Set<String> values, Instant loadedAt) {}
}
