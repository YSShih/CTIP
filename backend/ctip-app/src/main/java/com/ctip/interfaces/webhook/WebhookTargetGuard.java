package com.ctip.interfaces.webhook;

import com.ctip.domain.notification.WebhookTarget;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * 送達前的第二道 SSRF 防線:對<strong>解析後</strong>的位址判定,而不是對字串。
 *
 * <p>{@link WebhookTarget} 在建立時只看得到字串,擋得掉字面 IP;但
 * {@code https://evil.example/} 的 A 記錄可以就是 {@code 169.254.169.254},
 * 也可以在通過建立檢查之後才改過去(DNS rebinding)。因此每一次送達都要重新解析並重新判定
 * ——這是唯一能同時擋住這兩種情況的位置。
 *
 * <p>{@code InetAddress} 自帶的述詞不完整:{@code isSiteLocalAddress()} 對 IPv6 只認
 * 已廢止的 {@code fec0::/10},不認實際在用的 ULA {@code fc00::/7};IPv4 也不認 CGNAT
 * {@code 100.64/10} 與 metadata 之外的保留段。故一律換算成位元組後交給
 * {@link WebhookTarget} 的同一組判定,建立端與送達端因此不會漂移。
 */
final class WebhookTargetGuard {

    private WebhookTargetGuard() {}

    /** 送達端在解析後判定失敗時的原因字串;會寫進 {@code webhook_deliveries.error_message}。 */
    static final String BLOCKED_REASON = "target_not_publicly_routable";

    /**
     * @return true 表示這個 URL 現在可以送;false 表示它解析到本機/私有網路,或根本解不出來
     */
    static boolean isPubliclyRoutable(String targetUrl) {
        String host;
        try {
            host = new URI(targetUrl).getHost();
        } catch (java.net.URISyntaxException e) {
            return false;
        }
        if (host == null || WebhookTarget.isBlockedHost(host)) {
            return false;
        }
        InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(stripBrackets(host));
        } catch (UnknownHostException e) {
            // 解不出來就送不出去;當成「不可送」比讓 HttpClient 再解析一次好——
            // 那一次解析的結果不會經過本檢查(TOCTOU 只能靠這裡收斂到一次判定)
            return false;
        }
        for (InetAddress address : resolved) {
            if (isBlocked(address)) {
                return false;
            }
        }
        return resolved.length > 0;
    }

    private static boolean isBlocked(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return WebhookTarget.isBlockedIpv4(
                    new int[] {bytes[0] & 0xFF, bytes[1] & 0xFF, bytes[2] & 0xFF, bytes[3] & 0xFF});
        }
        int[] groups = new int[8];
        for (int i = 0; i < 8; i++) {
            groups[i] = (bytes[i * 2] & 0xFF) << 8 | (bytes[i * 2 + 1] & 0xFF);
        }
        return WebhookTarget.isBlockedIpv6(groups);
    }

    private static String stripBrackets(String host) {
        return host.length() > 1 && host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1)
                : host;
    }
}
