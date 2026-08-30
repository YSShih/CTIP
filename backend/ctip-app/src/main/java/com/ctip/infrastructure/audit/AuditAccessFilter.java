package com.ctip.infrastructure.audit;

import com.ctip.application.audit.AuditEvent;
import com.ctip.application.port.AuditPort;
import com.ctip.domain.audit.AuditAction;
import com.ctip.domain.audit.AuditResult;
import com.ctip.domain.plan.EndpointClass;
import com.ctip.infrastructure.ratelimit.EndpointClassifier;
import com.ctip.infrastructure.security.AuthState;
import com.ctip.infrastructure.security.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 以請求為觸發點的 17 種稽核行為(docs/spec/13-platform-ops.md §13.5 觸發點對照表),
 * 掛在 security filter chain 的<strong>尾端</strong>——{@code API_ACCESS} 的觸發點就是那裡。
 *
 * <p>寫入一律在回應完成之後:{@code result} 取自狀態碼(&lt; 400 → SUCCESS、401/403/429 → DENIED、
 * 其餘 → FAILURE),而稽核本身走非同步的 {@link AuditPort},不延長請求。
 */
public class AuditAccessFilter extends OncePerRequestFilter {

    /** 探針不是 API 存取(§10.7 對限流也是同一個豁免)。 */
    private static final String ACTUATOR_PREFIX = "/actuator";

    private final AuditPort audit;
    private final AuditSampler sampler;
    private final ObjectProvider<TenantContext> tenantContext;

    public AuditAccessFilter(AuditPort audit, AuditSampler sampler, ObjectProvider<TenantContext> tenantContext) {
        this.audit = audit;
        this.sampler = sampler;
        this.tenantContext = tenantContext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            record(request, response.getStatus());
        }
    }

    private void record(HttpServletRequest request, int status) {
        String path = EndpointClassifier.normalize(request.getRequestURI());
        if (path.startsWith(ACTUATOR_PREFIX)) {
            return;
        }
        AuditResult result = resultOf(status);
        AuditEndpoints.match(request.getMethod(), path).ifPresent(endpoint -> recordEndpoint(endpoint, result, path));
        recordApiAccess(request, path, status, result);
    }

    private void recordEndpoint(AuditEndpoints endpoint, AuditResult result, String path) {
        AuditAction action = resolve(endpoint.action(), result);
        // 由 IOC_QUERY 升級成 IOC_DOWNLOAD 的那一筆是 100%,不再取樣
        if (endpoint.sampled() && action == endpoint.action() && !sampler.keepRead()) {
            return;
        }
        audit.record(AuditEvent.of(action, result)
                .withResource(endpoint.resourceType(), endpoint.resourceId())
                .withMetadata(Map.of("path", path)));
    }

    /**
     * 兩個由執行結果決定的行為:登入失敗是另一個代碼;{@code GET /iocs} 的回應筆數
     * 超過單頁上限的一半即視為下載(§13.5 觸發點對照表)。
     */
    private static AuditAction resolve(AuditAction action, AuditResult result) {
        if (action == AuditAction.LOGIN && result != AuditResult.SUCCESS) {
            return AuditAction.LOGIN_FAILED;
        }
        boolean download =
                AuditSignals.currentPage().filter(AuditSignals.Page::isDownload).isPresent();
        return action == AuditAction.IOC_QUERY && download ? AuditAction.IOC_DOWNLOAD : action;
    }

    /** §13.5:security filter chain 尾端,所有<strong>已認證</strong>請求;寫 100% / 讀 1%。 */
    private void recordApiAccess(HttpServletRequest request, String path, int status, AuditResult result) {
        if (!isAuthenticated()) {
            return;
        }
        EndpointClass endpointClass = EndpointClassifier.classify(request.getMethod(), path);
        if (endpointClass == EndpointClass.READ && !sampler.keepRead()) {
            return;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("method", request.getMethod());
        metadata.put("path", path);
        metadata.put("status", status);
        audit.record(AuditEvent.of(AuditAction.API_ACCESS, result)
                .withResource("endpoint", null)
                .withMetadata(metadata));
    }

    /** 登入成功的那一次請求本身是匿名的,但它確實建立了一個已認證的身分——訊號視同已認證。 */
    private boolean isAuthenticated() {
        TenantContext context = tenantContext.getIfAvailable();
        return (context != null && context.authState() == AuthState.AUTHENTICATED)
                || AuditSignals.currentActor().isPresent();
    }

    private static AuditResult resultOf(int status) {
        if (status < 400) {
            return AuditResult.SUCCESS;
        }
        return switch (status) {
            case 401, 403, 429 -> AuditResult.DENIED;
            default -> AuditResult.FAILURE;
        };
    }
}
