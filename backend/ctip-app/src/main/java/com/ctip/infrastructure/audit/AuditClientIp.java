package com.ctip.infrastructure.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;

/**
 * {@code audit_logs.ip}(INET)的取值。
 *
 * <p>與限流的 {@code ClientIp} 刻意不同:限流把 IPv6 收斂成 {@code /64} 前綴(§10.7),
 * 稽核要的是**這一次請求的真實位址**。
 *
 * <p>{@code InetAddress.ofLiteral} 不做 DNS 查詢——{@code getByName} 會,而
 * {@code getRemoteAddr()} 的內容在錯設代理標頭時可能不是位址,那時一次稽核寫入
 * 就變成一次對外的名稱解析。
 */
final class AuditClientIp {

    private AuditClientIp() {}

    static String of(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (remote == null || remote.isBlank()) {
            return null;
        }
        int zone = remote.indexOf('%');
        String candidate = zone > 0 ? remote.substring(0, zone) : remote;
        try {
            return InetAddress.ofLiteral(candidate).getHostAddress();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
