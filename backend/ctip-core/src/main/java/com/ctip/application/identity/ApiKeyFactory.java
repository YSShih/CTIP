package com.ctip.application.identity;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.RolePermissionRepository;
import com.ctip.application.port.SecureTokenGeneratorPort;
import com.ctip.domain.identity.ApiKey;
import com.ctip.domain.identity.ApiKeyFormat;
import com.ctip.domain.identity.ApiKeyId;
import com.ctip.domain.identity.ApiKeySnapshot;
import com.ctip.domain.identity.IssuedApiKey;
import com.ctip.domain.identity.KeyHash;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 產生完整 key 與對應的聚合(不變量 K1、K2 由 {@link ApiKey#issue} 驗證)。 */
@Service
public class ApiKeyFactory {

    private final SecureTokenGeneratorPort tokenGenerator;
    private final IdGeneratorPort idGenerator;
    private final ClockPort clock;
    private final ApiKeySettings settings;
    private final RolePermissionRepository rolePermissions;

    public ApiKeyFactory(
            SecureTokenGeneratorPort tokenGenerator,
            IdGeneratorPort idGenerator,
            ClockPort clock,
            ApiKeySettings settings,
            RolePermissionRepository rolePermissions) {
        this.tokenGenerator = tokenGenerator;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.settings = settings;
        this.rolePermissions = rolePermissions;
    }

    /** {@code granted} 是建立者當下的權限;系統權限集合由此處自行取得(不變量 K3)。 */
    public IssuedApiKey create(ApiKeyIssueRequest request, Set<String> granted) {
        String randomSegment = tokenGenerator.randomBase62(ApiKeyFormat.RANDOM_SEGMENT_LENGTH);
        String fullKey = ApiKeyFormat.compose(settings.environment(), randomSegment);
        ApiKeySnapshot snapshot = new ApiKeySnapshot(
                new ApiKeyId(idGenerator.nextId()),
                request.tenantId(),
                request.userId(),
                request.name(),
                ApiKeyFormat.prefixOf(fullKey),
                KeyHash.of(fullKey),
                request.scopes(),
                request.expiresAt(),
                null,
                null,
                clock.now());
        return ApiKey.issue(snapshot, fullKey, rolePermissions.allPermissionCodes(), granted);
    }
}
