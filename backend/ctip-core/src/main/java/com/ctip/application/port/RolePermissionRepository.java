package com.ctip.application.port;

import com.ctip.application.rbac.RoleCode;
import java.util.Set;

/**
 * RBAC 參考資料(roles / permissions / role_permissions)。兩模型,無 domain model
 * (docs/spec/04-data-dictionary.md §4.1);由 V24 種入,可在資料庫調整。
 */
public interface RolePermissionRepository {

    /** 該角色的權限 code 集合;角色不存在時回空集合。 */
    Set<String> permissionsOf(RoleCode role);

    /** 系統定義的全部 permission code(不變量 K3 的判定依據)。 */
    Set<String> allPermissionCodes();
}
