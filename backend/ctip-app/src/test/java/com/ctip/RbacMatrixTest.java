package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctip.application.identity.AuthService;
import com.ctip.application.identity.AuthSession;
import com.ctip.application.port.RolePermissionRepository;
import com.ctip.application.port.TenantMembershipRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.support.RbacMatrix;
import com.ctip.support.TestIdentities;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * DoD M2-04:§10.3 角色與權限矩陣的<strong>每一格</strong>(22 權限 × 5 角色 = 110 格),
 * 以及 {@code @PreAuthorize} 於端點層確實生效。
 *
 * <p>「端點 → 需要哪個權限」是另一條軸,由 {@link EndpointAuthorizationTest} 守門。
 */
@AutoConfigureMockMvc
class RbacMatrixTest extends AbstractPostgresIntegrationTest {

    private static final String CLIENT_IP = "10.20.0.12";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RolePermissionRepository rolePermissions;

    @Autowired
    private AuthService authService;

    @Autowired
    private TenantMembershipRepository memberships;

    private TestIdentities identities;

    @BeforeEach
    void setUp() {
        identities = new TestIdentities(authService, memberships);
    }

    static Stream<Arguments> matrix() {
        return RbacMatrix.MATRIX.entrySet().stream()
                .flatMap(entry -> Stream.of(RoleCode.values())
                        .map(role -> Arguments.of(
                                entry.getKey(), role, entry.getValue().contains(role))));
    }

    @ParameterizedTest(name = "[{index}] {1} × {0} → {2}")
    @MethodSource("matrix")
    void everyCellOfThePermissionMatrix(String permission, RoleCode role, boolean granted) {
        assertThat(rolePermissions.permissionsOf(role).contains(permission))
                .as("角色 %s 對權限 %s 應為 %s", role, permission, granted)
                .isEqualTo(granted);
    }

    @Test
    void seedContainsExactlyTheSpecifiedPermissions() {
        assertThat(rolePermissions.allPermissionCodes())
                .containsExactlyInAnyOrderElementsOf(RbacMatrix.MATRIX.keySet());
    }

    /**
     * 第三份來源:規格 §10.3 的矩陣表本身。
     *
     * <p>矩陣有三份人工同步的來源——規格表、`V24`/`V27` 種子、測試常數 {@link RbacMatrix}。
     * 前兩份已由 {@link #everyCellOfThePermissionMatrix} 與上一條綁在一起,規格表卻只能靠人眼比對。
     * 本測試直接解析 §10.3 的 Markdown 表,讓三份來源任一漂移都會轉紅(ADR 0017)。
     *
     * <p>順帶消滅寫死的權限總數:原本的 {@code hasSize(21)} 每加一個權限就要改一處,
     * 而規格表才是那個數字的真正來源。
     */
    @Test
    void theSpecificationMatrixMatchesTheSeededMatrix() {
        assertThat(RbacMatrix.parseSpecificationTable())
                .as("docs/spec/10-identity-plans.md §10.3 的矩陣表與種子/常數不一致")
                .isEqualTo(RbacMatrix.MATRIX);
    }

    /** 矩陣的單調性:上層角色涵蓋下層角色的全部權限。 */
    @Test
    void higherRolesIncludeLowerRolePermissions() {
        List<RoleCode> ascending = List.of(
                RoleCode.ANONYMOUS, RoleCode.USER, RoleCode.PREMIUM_USER, RoleCode.TENANT_ADMIN, RoleCode.SYSTEM_ADMIN);
        for (int i = 1; i < ascending.size(); i++) {
            Set<String> lower = rolePermissions.permissionsOf(ascending.get(i - 1));
            assertThat(rolePermissions.permissionsOf(ascending.get(i)))
                    .as("%s 應涵蓋 %s", ascending.get(i), ascending.get(i - 1))
                    .containsAll(lower);
        }
    }

    /** {@code @PreAuthorize} 生效(否定面):匿名角色無 stix:export → 403 權限不足。 */
    @Test
    void preAuthorizeRejectsAnonymousOnPermissionedEndpoint() throws Exception {
        mvc.perform(asClient(get("/api/v1/stix/bundle"))).andExpect(status().isForbidden());
    }

    /** {@code @PreAuthorize} 生效(肯定面):USER 具備 stix:export → 通過授權。 */
    @Test
    void preAuthorizeAdmitsRoleThatHoldsThePermission() throws Exception {
        AuthSession user = identities.register("rbac-user@example.org", RoleCode.USER);
        mvc.perform(asClient(get("/api/v1/stix/bundle")).header("Authorization", TestIdentities.bearer(user)))
                .andExpect(status().isOk());
    }

    /** 已認證但權限不足 → 403(非 401):ANONYMOUS 角色的使用者無 apikey:create。 */
    @Test
    void preAuthorizeRejectsAuthenticatedUserLackingThePermission() throws Exception {
        AuthSession weak = identities.register("rbac-noperm@example.org", RoleCode.ANONYMOUS);
        mvc.perform(asClient(get("/api/v1/api-keys")).header("Authorization", TestIdentities.bearer(weak)))
                .andExpect(status().isForbidden());
    }

    /** 匿名具備 ioc:read,讀取端點不得因為導入 RBAC 而關閉(§10.2)。 */
    @Test
    void anonymousRetainsIocReadAccess() throws Exception {
        mvc.perform(asClient(get("/api/v1/iocs?limit=1"))).andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder asClient(MockHttpServletRequestBuilder builder) {
        return builder.with(request -> {
            request.setRemoteAddr(CLIENT_IP);
            return request;
        });
    }
}
