package com.ctip.infrastructure.audit;

import com.ctip.domain.audit.AuditAction;
import java.util.Optional;
import java.util.UUID;

/**
 * HTTP 端點 → 稽核行為的對照(docs/spec/13-platform-ops.md §13.5 觸發點對照表)。
 *
 * <p>把 17 種「以請求為觸發點」的行為集中在這一個表上,而不是散在各 controller:
 * 對照表是強制規格,散開之後沒有任何一處看得出「26 種行為是否都有人負責」。
 * 其餘 9 種以 domain event 為觸發點,見 {@code AuditEventListener}。
 *
 * @param sampled true = 依 {@code AUDIT_SAMPLE_READ_RATE} 取樣(§13.5 規則 4 的 1% 讀取)
 */
record AuditEndpoints(AuditAction action, String resourceType, UUID resourceId, boolean sampled) {

    private static final String PREFIX = "/api/v1/";

    static Optional<AuditEndpoints> match(String method, String normalizedPath) {
        if (!normalizedPath.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String[] parts = normalizedPath.substring(PREFIX.length()).split("/");
        return switch (parts[0]) {
            case "auth" -> auth(method, parts);
            case "iocs" -> iocs(method, parts);
            case "stix" -> stix(method, parts);
            case "sync" -> sync(method, parts);
            case "webhooks" -> webhooks(method, parts);
            case "admin" -> admin(parts);
            default -> Optional.empty();
        };
    }

    private static Optional<AuditEndpoints> auth(String method, String[] parts) {
        if (!"POST".equals(method) || parts.length != 2) {
            return Optional.empty();
        }
        return switch (parts[1]) {
            // 成功與失敗的行為代碼不同;由 AuditAccessFilter 依回應狀態改寫為 LOGIN_FAILED
            case "login" -> of(AuditAction.LOGIN, "user");
            case "logout" -> of(AuditAction.LOGOUT, "user");
            case "refresh" -> of(AuditAction.TOKEN_REFRESH, "refresh_token");
            default -> Optional.empty();
        };
    }

    private static Optional<AuditEndpoints> iocs(String method, String[] parts) {
        if (parts.length == 1) {
            // GET /iocs 的 IOC_QUERY / IOC_DOWNLOAD 之分由回應筆數決定(AuditAccessFilter)
            if ("GET".equals(method)) {
                return sampledOf(AuditAction.IOC_QUERY, "indicator");
            }
            return "POST".equals(method) ? of(AuditAction.IOC_SUBMIT, "indicator") : Optional.empty();
        }
        if ("POST".equals(method) && parts.length == 2) {
            return switch (parts[1]) {
                case "search", "lookup" -> sampledOf(AuditAction.IOC_QUERY, "indicator");
                case "import" -> of(AuditAction.IOC_IMPORT, "import_job");
                default -> Optional.empty();
            };
        }
        return parts.length == 3 ? iocsById(method, parts) : Optional.empty();
    }

    private static Optional<AuditEndpoints> iocsById(String method, String[] parts) {
        UUID id = uuidOrNull(parts[1]);
        if ("GET".equals(method) && "sources".equals(parts[2])) {
            return of(AuditAction.IOC_DOWNLOAD, "indicator", id);
        }
        if ("POST".equals(method) && "report-false-positive".equals(parts[2])) {
            return of(AuditAction.IOC_REPORT_FP, "indicator", id);
        }
        return Optional.empty();
    }

    private static Optional<AuditEndpoints> stix(String method, String[] parts) {
        return "GET".equals(method) && parts.length == 2 && "bundle".equals(parts[1])
                ? of(AuditAction.STIX_EXPORT, "stix_bundle")
                : Optional.empty();
    }

    private static Optional<AuditEndpoints> sync(String method, String[] parts) {
        if (!"GET".equals(method) || parts.length != 2) {
            return Optional.empty();
        }
        return switch (parts[1]) {
            case "manifest" -> sampledOf(AuditAction.SYNC_MANIFEST, "bloom_version");
            case "bloom" -> of(AuditAction.SYNC_BLOOM, "bloom_artifact");
            case "delta" -> of(AuditAction.SYNC_DELTA, "bloom_artifact");
            default -> Optional.empty();
        };
    }

    private static Optional<AuditEndpoints> webhooks(String method, String[] parts) {
        if ("POST".equals(method) && parts.length == 1) {
            return of(AuditAction.WEBHOOK_CREATED, "webhook");
        }
        return "DELETE".equals(method) && parts.length == 2
                ? of(AuditAction.WEBHOOK_DELETED, "webhook", uuidOrNull(parts[1]))
                : Optional.empty();
    }

    /** §13.5:所有 {@code /api/v1/admin/**} 端點,{@code resource_type} 依端點。 */
    private static Optional<AuditEndpoints> admin(String[] parts) {
        String resourceType = parts.length > 1 ? parts[1] : "admin";
        UUID id = parts.length > 2 ? uuidOrNull(parts[2]) : null;
        return of(AuditAction.ADMIN_ACTION, resourceType, id);
    }

    private static Optional<AuditEndpoints> of(AuditAction action, String resourceType) {
        return of(action, resourceType, null);
    }

    private static Optional<AuditEndpoints> of(AuditAction action, String resourceType, UUID resourceId) {
        return Optional.of(new AuditEndpoints(action, resourceType, resourceId, false));
    }

    private static Optional<AuditEndpoints> sampledOf(AuditAction action, String resourceType) {
        return Optional.of(new AuditEndpoints(action, resourceType, null, true));
    }

    private static UUID uuidOrNull(String candidate) {
        try {
            return UUID.fromString(candidate);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
