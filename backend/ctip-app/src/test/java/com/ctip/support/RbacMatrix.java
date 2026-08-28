package com.ctip.support;

import com.ctip.application.rbac.RoleCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * docs/spec/10-identity-plans.md §10.3 的角色與權限矩陣,逐格謄寫。
 *
 * <p>這是矩陣的第二份獨立來源(第一份是 {@code V24__seed_rbac.sql} + {@code V27__seed_rbac_read_permissions.sql}),
 * 兩者互為驗證——種子改動而規格未改,測試立即失敗。§10.3 標題原誤寫「18 項」,實際清單為 19 個字串
 * (ADR 0012 決策 1);Phase 13 收尾稽核補入 {@code source:read} / {@code stats:read} 後為 21 個
 * (ADR 0013)。
 */
public final class RbacMatrix {

    private static final Set<RoleCode> ALL = Set.of(RoleCode.values());
    private static final Set<RoleCode> LOGGED_IN =
            Set.of(RoleCode.USER, RoleCode.PREMIUM_USER, RoleCode.TENANT_ADMIN, RoleCode.SYSTEM_ADMIN);
    private static final Set<RoleCode> PREMIUM_UP =
            Set.of(RoleCode.PREMIUM_USER, RoleCode.TENANT_ADMIN, RoleCode.SYSTEM_ADMIN);
    private static final Set<RoleCode> ADMIN_UP = Set.of(RoleCode.TENANT_ADMIN, RoleCode.SYSTEM_ADMIN);
    private static final Set<RoleCode> SYSTEM_ONLY = Set.of(RoleCode.SYSTEM_ADMIN);

    /** permission code → 擁有該權限的角色集合。 */
    public static final Map<String, Set<RoleCode>> MATRIX = matrix();

    private RbacMatrix() {}

    private static Map<String, Set<RoleCode>> matrix() {
        Map<String, Set<RoleCode>> cells = new LinkedHashMap<>();
        cells.put("ioc:read", ALL);
        cells.put("threat:read", ALL);
        cells.put("sync:bloom", ALL);
        cells.put("source:read", ALL);
        cells.put("stats:read", ALL);
        cells.put("ioc:export", LOGGED_IN);
        cells.put("stix:export", LOGGED_IN);
        cells.put("sync:delta", LOGGED_IN);
        cells.put("ioc:report-fp", LOGGED_IN);
        cells.put("apikey:create", LOGGED_IN);
        cells.put("apikey:revoke", LOGGED_IN);
        cells.put("ioc:submit", PREMIUM_UP);
        cells.put("ioc:import", PREMIUM_UP);
        cells.put("webhook:manage", PREMIUM_UP);
        cells.put("user:manage", ADMIN_UP);
        cells.put("tenant:manage", ADMIN_UP);
        cells.put("audit:read", ADMIN_UP);
        cells.put("ioc:publish", SYSTEM_ONLY);
        cells.put("source:manage", SYSTEM_ONLY);
        cells.put("source:sync", SYSTEM_ONLY);
        cells.put("system:admin", SYSTEM_ONLY);
        return Map.copyOf(cells);
    }

    /** §10.3 表頭的角色順序。 */
    private static final List<RoleCode> COLUMNS = List.of(
            RoleCode.ANONYMOUS, RoleCode.USER, RoleCode.PREMIUM_USER, RoleCode.TENANT_ADMIN, RoleCode.SYSTEM_ADMIN);

    /**
     * 解析 `docs/spec/10-identity-plans.md` §10.3 的矩陣表。
     *
     * <p>列格式為 {@code | `code` | ✓ | — | … |};一列可帶兩個以 ` / ` 分隔的權限
     * (例如 {@code `apikey:create` / `apikey:revoke`}),兩者共用同一組勾選。
     */
    public static Map<String, Set<RoleCode>> parseSpecificationTable() {
        Path spec = Path.of("")
                .toAbsolutePath()
                .resolve("../../docs/spec/10-identity-plans.md")
                .normalize();
        String text;
        try {
            text = Files.readString(spec);
        } catch (IOException e) {
            throw new IllegalStateException("讀不到規格 " + spec, e);
        }
        int start = text.indexOf("### 角色與權限矩陣");
        int end = text.indexOf("### 實作要求", start);
        if (start < 0 || end < 0) {
            throw new IllegalStateException("§10.3 的矩陣表章節找不到——標題是否被改過?");
        }

        Pattern row = Pattern.compile("^\\|\\s*(`[^|]+`)\\s*\\|([^\\n]*)\\|\\s*$", Pattern.MULTILINE);
        Map<String, Set<RoleCode>> parsed = new LinkedHashMap<>();
        Matcher matcher = row.matcher(text.substring(start, end));
        while (matcher.find()) {
            List<String> codes = Arrays.stream(matcher.group(1).split("/"))
                    .map(code -> code.replace("`", "").trim())
                    .filter(code -> !code.isEmpty())
                    .toList();
            String[] cells = matcher.group(2).split("\\|", -1);
            Set<RoleCode> granted = new LinkedHashSet<>();
            for (int i = 0; i < COLUMNS.size() && i < cells.length; i++) {
                if (cells[i].trim().equals("\u2713")) {
                    granted.add(COLUMNS.get(i));
                }
            }
            codes.forEach(code -> parsed.put(code, granted));
        }
        if (parsed.isEmpty()) {
            throw new IllegalStateException("§10.3 的矩陣表解析不出任何一列——表格格式是否被改過?");
        }
        return parsed;
    }
}
