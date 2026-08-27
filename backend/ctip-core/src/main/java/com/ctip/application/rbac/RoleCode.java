package com.ctip.application.rbac;

/**
 * 五個角色(docs/spec/10-identity-plans.md §10.3)。角色→權限對應存於資料庫可調整,
 * 此列舉只固定「有哪些角色」——與 {@code ck_roles_code} 一致。
 */
public enum RoleCode {
    ANONYMOUS,
    USER,
    PREMIUM_USER,
    TENANT_ADMIN,
    SYSTEM_ADMIN;

    /** SYSTEM_ADMIN 為跨租戶角色(roles.tenant_scoped = false)。 */
    public boolean isTenantScoped() {
        return this != SYSTEM_ADMIN;
    }
}
