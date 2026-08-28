package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.port.RolePermissionRepository;
import com.ctip.application.rbac.RoleCode;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 「端點 → 需要哪個權限」這條軸的守門(docs/spec/10-identity-plans.md §10.3 實作要求)。
 *
 * <p>{@code SecurityConfig} 是 {@code anyRequest().permitAll()} + 純方法層授權,
 * 因此<strong>沒有 {@code @PreAuthorize} 的 handler 等於完全開放</strong>——
 * Phase 13 收尾稽核就是這樣漏掉 {@code /sources} ×3 與 {@code /stats} ×2(ADR 0013)。
 * 這個測試逐一列舉每個 handler,要求它「有授權標註」或「在明列的免授權白名單內」。
 *
 * <p>{@link RbacMatrixTest} 守的是另一條軸:權限 × 角色的 105 格。
 */
@AutoConfigureMockMvc
class EndpointAuthorizationTest extends AbstractPostgresIntegrationTest {

    /**
     * 唯一允許不帶 {@code @PreAuthorize} 的端點。
     *
     * <p>{@code /health}、{@code /version} 不讀任何情資;{@code /auth/*} 是取得憑證的入口,
     * 要求權限會造成雞生蛋(§9.1 明文四者匿名可存取)。新增項目必須連同理由一起加。
     */
    private static final Set<String> UNAUTHORIZED_BY_DESIGN = Set.of(
            "GET /api/v1/health",
            "GET /api/v1/version",
            "POST /api/v1/auth/register",
            "POST /api/v1/auth/login",
            "POST /api/v1/auth/refresh",
            "POST /api/v1/auth/logout");

    private static final Pattern AUTHORITY = Pattern.compile("hasAuthority\\('([a-z]+:[a-z-]+)'\\)");

    /** actuator 也貢獻一個 RequestMappingHandlerMapping,必須指名 MVC 的那一個。 */
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private RolePermissionRepository rolePermissions;

    /** 每一個 CTIP handler 都必須宣告授權,否則就得明白列進白名單。 */
    @Test
    void everyEndpointDeclaresItsAuthorization() {
        List<String> undeclared = ctipHandlers()
                .filter(entry -> preAuthorizeOf(entry.getValue()) == null)
                .map(entry -> describe(entry.getKey()))
                .filter(route -> !UNAUTHORIZED_BY_DESIGN.contains(route))
                .sorted()
                .toList();
        assertThat(undeclared)
                .as("這些端點沒有 @PreAuthorize,而 filter chain 是 permitAll —— 等於完全開放")
                .isEmpty();
    }

    /** 白名單不得放進已經有標註的端點,避免它日後被誤刪標註也沒人發現。 */
    @Test
    void allowlistContainsOnlyEndpointsThatReallyHaveNoAnnotation() {
        Set<String> annotated = ctipHandlers()
                .filter(entry -> preAuthorizeOf(entry.getValue()) != null)
                .map(entry -> describe(entry.getKey()))
                .collect(Collectors.toSet());
        assertThat(UNAUTHORIZED_BY_DESIGN).doesNotContainAnyElementsOf(annotated);
    }

    /** 每個被引用的權限都必須真的存在於種子;拼錯的 code 會讓端點對所有人 403。 */
    @Test
    void everyReferencedAuthorityExistsInTheSeededMatrix() {
        Set<String> referenced = new TreeSet<>();
        ctipHandlers().forEach(entry -> {
            PreAuthorize annotation = preAuthorizeOf(entry.getValue());
            if (annotation != null) {
                Matcher matcher = AUTHORITY.matcher(annotation.value());
                while (matcher.find()) {
                    referenced.add(matcher.group(1));
                }
            }
        });
        assertThat(referenced).isNotEmpty();
        assertThat(rolePermissions.allPermissionCodes()).containsAll(referenced);
    }

    /** §9.1 標「匿名」的端點,其所需權限必須真的在 ANONYMOUS 角色手上。 */
    @Test
    void anonymousAccessibleEndpointsRequireOnlyAnonymousPermissions() {
        Set<String> anonymous = rolePermissions.permissionsOf(RoleCode.ANONYMOUS);
        assertThat(anonymous).contains("ioc:read", "source:read", "stats:read", "threat:read", "sync:bloom");
    }

    private java.util.stream.Stream<java.util.Map.Entry<RequestMappingInfo, HandlerMethod>> ctipHandlers() {
        return handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue().getBeanType().getPackageName().startsWith("com.ctip"));
    }

    /** 標註可能寫在 controller 方法上,也可能寫在它實作的 openapi 文件介面上。 */
    private static PreAuthorize preAuthorizeOf(HandlerMethod handler) {
        Method method = handler.getMethod();
        PreAuthorize direct = method.getAnnotation(PreAuthorize.class);
        if (direct != null) {
            return direct;
        }
        for (Class<?> iface : handler.getBeanType().getInterfaces()) {
            try {
                PreAuthorize inherited = iface.getMethod(method.getName(), method.getParameterTypes())
                        .getAnnotation(PreAuthorize.class);
                if (inherited != null) {
                    return inherited;
                }
            } catch (NoSuchMethodException ignored) {
                // 該介面沒有這個方法,換下一個
            }
        }
        return null;
    }

    private static String describe(RequestMappingInfo mapping) {
        String method = mapping.getMethodsCondition().getMethods().stream()
                .findFirst()
                .map(Enum::name)
                .orElse("ANY");
        String path = mapping.getPathPatternsCondition() == null
                ? String.valueOf(mapping.getPatternValues())
                : mapping.getPathPatternsCondition().getPatternValues().stream()
                        .findFirst()
                        .orElse("?");
        return method + " " + path;
    }
}
