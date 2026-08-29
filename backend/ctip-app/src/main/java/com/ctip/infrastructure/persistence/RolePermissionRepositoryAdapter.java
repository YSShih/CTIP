package com.ctip.infrastructure.persistence;

import com.ctip.application.port.CachePort;
import com.ctip.application.port.RolePermissionRepository;
import com.ctip.application.rbac.RoleCode;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * RBAC 參考資料的讀取。角色→權限對應存於資料庫(V24 種入),此處不硬編任何一格。
 *
 * <p>匿名請求也要取得 ANONYMOUS 角色的權限,若每請求查一次 DB 會成為讀取路徑的固定成本;
 * 由於本表只由 migration 變更,以 60 秒 TTL 快取(仍保留 §10.3「可在資料庫調整」的語意,
 * 最多延遲一分鐘生效)。Phase 17 起改用 {@link CachePort}——原本是本類別自有的
 * {@code ConcurrentHashMap},多實例下每個實例各存一份、也無從失效。
 *
 * <p>權限碼的字元集是 {@code [a-z:-]}(V24／V27／V29 的種子),因此以逗號串接即可,
 * 不需要 JSON:少一個序列化器,就少一種「快取值解不開」的失敗模式。
 */
@Repository
@Transactional(readOnly = true)
class RolePermissionRepositoryAdapter implements RolePermissionRepository {

    private static final Duration TTL = Duration.ofSeconds(60);
    private static final String KEY_PREFIX = "rbac:permissions:";
    private static final String ALL_PERMISSIONS = "*";
    private static final String SEPARATOR = ",";

    private final RoleJpaRepository roles;
    private final PermissionJpaRepository permissions;
    private final CachePort cache;

    RolePermissionRepositoryAdapter(RoleJpaRepository roles, PermissionJpaRepository permissions, CachePort cache) {
        this.roles = roles;
        this.permissions = permissions;
        this.cache = cache;
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

    private Set<String> cached(String key, Supplier<Set<String>> loader) {
        String cacheKey = KEY_PREFIX + key;
        Optional<String> hit = cache.get(cacheKey);
        if (hit.isPresent()) {
            return decode(hit.get());
        }
        Set<String> loaded = loader.get();
        cache.put(cacheKey, String.join(SEPARATOR, loaded), TTL);
        return loaded;
    }

    /** 空字串是「這個角色沒有任何權限」,不是「沒有快取」——split 對空字串會給出一個空元素。 */
    private static Set<String> decode(String encoded) {
        return encoded.isEmpty()
                ? Set.of()
                : Arrays.stream(encoded.split(SEPARATOR)).collect(Collectors.toUnmodifiableSet());
    }
}
