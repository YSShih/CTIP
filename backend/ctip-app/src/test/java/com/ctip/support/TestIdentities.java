package com.ctip.support;

import com.ctip.application.identity.AuthCommands;
import com.ctip.application.identity.AuthService;
import com.ctip.application.identity.AuthSession;
import com.ctip.application.identity.ClientInfo;
import com.ctip.application.port.TenantMembershipRepository;
import com.ctip.application.rbac.RoleCode;

/**
 * 整合測試共用的身分建立:走真實的 register / login 流程,不直接寫資料表——
 * 測試因此同時覆蓋註冊路徑,而不是繞過它。
 */
public final class TestIdentities {

    /** 測試密碼固定值(§14.7:樣本資料不得含真實 secret,且必須是明顯的測試值)。 */
    public static final String PASSWORD = "test-password-1234";

    private final AuthService authService;
    private final TenantMembershipRepository memberships;

    public TestIdentities(AuthService authService, TenantMembershipRepository memberships) {
        this.authService = authService;
        this.memberships = memberships;
    }

    /** 註冊一位新使用者(自帶新租戶),再把其角色改為指定值後重新登入以取得對應 claims。 */
    public AuthSession register(String email, RoleCode role) {
        AuthSession registered = authService.register(
                new AuthCommands.Register(email, PASSWORD, "Test " + role, null), ClientInfo.unknown());
        if (role == RoleCode.TENANT_ADMIN) {
            return registered;
        }
        memberships.assign(
                registered.identity().tenantId(), registered.identity().userId(), role);
        return login(email);
    }

    public AuthSession login(String email) {
        return authService.login(new AuthCommands.Login(email, PASSWORD, "junit", "127.0.0.1"));
    }

    public static String bearer(AuthSession session) {
        return "Bearer " + session.accessToken();
    }
}
