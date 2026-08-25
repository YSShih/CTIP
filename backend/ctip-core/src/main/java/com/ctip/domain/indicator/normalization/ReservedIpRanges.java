package com.ctip.domain.indicator.normalization;

/**
 * 私有/保留位址判定(docs/spec/07-domain-intel.md §7.3 的 PRIVATE_OR_RESERVED_IP):
 * IPv4 — 10/8、172.16/12、192.168/16、127/8、169.254/16、100.64/10、0/8、224/4;
 * IPv6 — ::1、fc00::/7、fe80::/10。輸入為正規化後的值。
 */
public final class ReservedIpRanges {

    private ReservedIpRanges() {}

    public static boolean isReservedIpv4(String normalized) {
        int[] o = Ipv4Normalizer.octets(normalized);
        return o[0] == 0
                || o[0] == 10
                || o[0] == 127
                || (o[0] == 100 && o[1] >= 64 && o[1] <= 127)
                || (o[0] == 169 && o[1] == 254)
                || (o[0] == 172 && o[1] >= 16 && o[1] <= 31)
                || (o[0] == 192 && o[1] == 168)
                || (o[0] >= 224 && o[0] <= 239);
    }

    public static boolean isReservedIpv6(String normalized) {
        int[] g = Ipv6Normalizer.groups(normalized);
        boolean loopback =
                g[0] == 0 && g[1] == 0 && g[2] == 0 && g[3] == 0 && g[4] == 0 && g[5] == 0 && g[6] == 0 && g[7] == 1;
        return loopback || (g[0] & 0xFE00) == 0xFC00 || (g[0] & 0xFFC0) == 0xFE80;
    }
}
