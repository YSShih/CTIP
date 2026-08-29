package com.ctip.domain.notification;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * 不變量 W1 的完整形式:{@code targetUrl} 必須是 https,而且<strong>必須指向平台外部</strong>。
 *
 * <p>只檢查 {@code https://} 是不夠的。送達路徑是「伺服器主動對租戶指定的 URL 發出 POST」,
 * 那正是 SSRF 的定義:租戶把 {@code https://169.254.169.254/latest/meta-data/}、
 * {@code https://localhost:9200/_cluster/health} 或 {@code https://10.0.0.5:8080/admin} 存進來,
 * 平台就會替它去打自己網路裡的東西,並把結果的狀態碼與延遲寫進 {@code webhook_deliveries}
 * ——即使回應本文被丟棄,那仍是一台可用的內網掃描器與雲端 metadata 取用管道。
 *
 * <p>本類別只做<strong>純字串/數值</strong>判定(domain 不得做 IO,ArchUnit 規則 1/9):
 * 字面 IP 位址與已知的本機名稱。以主機名繞過(DNS 指向 127.0.0.1)與 DNS rebinding
 * 由送達端在解析之後再擋一次——兩道防線缺一不可,任一單獨都擋不住另一種。
 */
public final class WebhookTarget {

    private static final String REQUIRED_SCHEME = "https";

    /** 解析不出 IP 的名稱裡,已知一定指回本機或內部網路的那些。 */
    private static final Set<String> BLOCKED_HOST_SUFFIXES =
            Set.of(".localhost", ".local", ".internal", ".localdomain");

    private static final Set<String> BLOCKED_HOSTS = Set.of("localhost", "localhost.localdomain");

    private WebhookTarget() {}

    /** @return 原樣的 url(通過檢查時);否則丟 {@link IllegalArgumentException} */
    public static String require(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("targetUrl 不得為空(不變量 W1)");
        }
        URI uri = parse(url);
        if (!REQUIRED_SCHEME.equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("targetUrl 必須為 https://(不變量 W1):" + url);
        }
        if (uri.getUserInfo() != null) {
            // URL 內嵌帳密會被原樣寫進 webhooks.target_url,等於把憑證以明文存進資料庫
            throw new IllegalArgumentException("targetUrl 不得內嵌認證資訊(不變量 W1)");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("targetUrl 必須有明確的主機名(不變量 W1):" + url);
        }
        if (isBlockedHost(host)) {
            throw new IllegalArgumentException("targetUrl 不得指向本機或私有網路(不變量 W1):" + url);
        }
        return url;
    }

    /** 主機名(或字面 IP)是否指向本機/私有網路。送達端會對 <em>解析後</em> 的位址再問一次。 */
    public static boolean isBlockedHost(String host) {
        String lower = stripIpv6Brackets(host).toLowerCase(Locale.ROOT);
        if (BLOCKED_HOSTS.contains(lower) || BLOCKED_HOST_SUFFIXES.stream().anyMatch(lower::endsWith)) {
            return true;
        }
        int[] ipv4 = parseIpv4(lower);
        if (ipv4 != null) {
            return isBlockedIpv4(ipv4);
        }
        int[] ipv6 = parseIpv6(lower);
        return ipv6 != null && isBlockedIpv6(ipv6);
    }

    /**
     * 0/8、10/8、100.64/10(CGNAT)、127/8、169.254/16(雲端 metadata)、172.16/12、
     * 192.0.0/24、192.168/16、198.18/15(benchmark)、224/4(multicast)、240/4(含 broadcast)。
     */
    public static boolean isBlockedIpv4(int[] o) {
        return o[0] == 0
                || o[0] == 10
                || o[0] == 127
                || (o[0] == 100 && o[1] >= 64 && o[1] <= 127)
                || (o[0] == 169 && o[1] == 254)
                || (o[0] == 172 && o[1] >= 16 && o[1] <= 31)
                || (o[0] == 192 && o[1] == 0 && o[2] == 0)
                || (o[0] == 192 && o[1] == 168)
                || (o[0] == 198 && (o[1] == 18 || o[1] == 19))
                || o[0] >= 224;
    }

    /** ::、::1、fc00::/7(ULA)、fe80::/10(link-local)、ff00::/8(multicast)、IPv4-mapped。 */
    public static boolean isBlockedIpv6(int[] g) {
        boolean unspecifiedOrLoopback = g[0] == 0
                && g[1] == 0
                && g[2] == 0
                && g[3] == 0
                && g[4] == 0
                && g[5] == 0
                && g[6] == 0
                && (g[7] == 0 || g[7] == 1);
        if (unspecifiedOrLoopback) {
            return true;
        }
        // ::ffff:a.b.c.d —— 用 IPv6 語法包一個 IPv4 位址,不得因此繞過 IPv4 的判定
        if (g[0] == 0 && g[1] == 0 && g[2] == 0 && g[3] == 0 && g[4] == 0 && g[5] == 0xFFFF) {
            return isBlockedIpv4(new int[] {g[6] >>> 8, g[6] & 0xFF, g[7] >>> 8, g[7] & 0xFF});
        }
        return (g[0] & 0xFE00) == 0xFC00 || (g[0] & 0xFFC0) == 0xFE80 || (g[0] & 0xFF00) == 0xFF00;
    }

    private static URI parse(String url) {
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("targetUrl 不是合法的 URL(不變量 W1):" + url);
        }
    }

    private static String stripIpv6Brackets(String host) {
        return host.length() > 1 && host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1)
                : host;
    }

    /** @return 四個 octet,或 null 表示這不是點分十進位的 IPv4 字面值 */
    private static int[] parseIpv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            if (parts[i].isEmpty() || parts[i].length() > 3 || !parts[i].chars().allMatch(Character::isDigit)) {
                return null;
            }
            octets[i] = Integer.parseInt(parts[i]);
            if (octets[i] > 255) {
                return null;
            }
        }
        return octets;
    }

    /** @return 八組 16 位元,或 null 表示這不是 IPv6 字面值(含 zone id 的一律視為本機,由呼叫端擋) */
    private static int[] parseIpv6(String host) {
        if (host.indexOf(':') < 0) {
            return null;
        }
        String address = host.contains("%") ? host.substring(0, host.indexOf('%')) : host;
        String[] halves = address.split("::", -1);
        if (halves.length > 2) {
            return null;
        }
        int[] groups = new int[8];
        int[] head = parseGroups(halves[0]);
        int[] tail = halves.length == 2 ? parseGroups(halves[1]) : new int[0];
        if (head == null || tail == null || head.length + tail.length > 8) {
            return null;
        }
        if (halves.length == 1 && head.length != 8) {
            return null;
        }
        System.arraycopy(head, 0, groups, 0, head.length);
        System.arraycopy(tail, 0, groups, 8 - tail.length, tail.length);
        return groups;
    }

    /** 末段允許點分十進位(RFC 4291 的 {@code ::ffff:127.0.0.1} 寫法),展開成兩組 16 位元。 */
    private static int[] parseGroups(String part) {
        if (part.isEmpty()) {
            return new int[0];
        }
        String[] tokens = part.split(":", -1);
        int last = tokens.length - 1;
        int[] embedded = tokens[last].indexOf('.') < 0 ? null : parseIpv4(tokens[last]);
        if (tokens[last].indexOf('.') >= 0 && embedded == null) {
            return null;
        }
        int[] groups = new int[embedded == null ? tokens.length : tokens.length + 1];
        int hexTokens = embedded == null ? tokens.length : last;
        for (int i = 0; i < hexTokens; i++) {
            if (tokens[i].isEmpty() || tokens[i].length() > 4) {
                return null;
            }
            try {
                groups[i] = Integer.parseInt(tokens[i], 16);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (embedded != null) {
            groups[last] = embedded[0] << 8 | embedded[1];
            groups[last + 1] = embedded[2] << 8 | embedded[3];
        }
        return groups;
    }
}
