package com.ctip.domain.indicator.normalization;

import com.ctip.sdk.IocType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * IPV6:依 RFC 5952 壓縮——最長零段(≥ 2 組)以 {@code ::} 取代(相同長度取最左)、
 * 小寫十六進位、去除前導零(docs/spec/07-domain-intel.md §7.2)。
 * 內嵌 IPv4 尾段(如 ::ffff:1.2.3.4)解析後以純十六進位輸出。
 */
final class Ipv6Normalizer implements IocNormalizer {

    private static final int GROUPS = 8;

    @Override
    public IocType type() {
        return IocType.IPV6;
    }

    @Override
    public String normalize(String cleanedValue) {
        int[] groups = groups(cleanedValue);
        int[] run = longestZeroRun(groups);
        if (run[1] < 2) {
            return joinHex(groups, 0, GROUPS);
        }
        return joinHex(groups, 0, run[0]) + "::" + joinHex(groups, run[0] + run[1], GROUPS);
    }

    /** 解析為 8 個 16-bit 組;格式不合丟 IocFormatException。 */
    static int[] groups(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        int doubleColon = lower.indexOf("::");
        if (doubleColon >= 0 && lower.indexOf("::", doubleColon + 1) >= 0) {
            throw new IocFormatException("IPv6 只允許一個 :::" + value);
        }
        List<Integer> head = doubleColon >= 0 ? parseSide(lower.substring(0, doubleColon), value) : null;
        List<Integer> tail;
        if (doubleColon >= 0) {
            tail = parseSide(lower.substring(doubleColon + 2), value);
        } else {
            tail = parseSide(lower, value);
        }
        return assemble(head, tail, value);
    }

    private static int[] assemble(List<Integer> head, List<Integer> tail, String value) {
        int[] groups = new int[GROUPS];
        if (head == null) {
            if (tail.size() != GROUPS) {
                throw new IocFormatException("IPv6 必須是 8 組:" + value);
            }
            for (int i = 0; i < GROUPS; i++) {
                groups[i] = tail.get(i);
            }
            return groups;
        }
        if (head.size() + tail.size() > GROUPS - 1) {
            throw new IocFormatException("IPv6 的 :: 至少要涵蓋一組零:" + value);
        }
        for (int i = 0; i < head.size(); i++) {
            groups[i] = head.get(i);
        }
        for (int i = 0; i < tail.size(); i++) {
            groups[GROUPS - tail.size() + i] = tail.get(i);
        }
        return groups;
    }

    private static List<Integer> parseSide(String side, String whole) {
        List<Integer> groups = new ArrayList<>();
        if (side.isEmpty()) {
            return groups;
        }
        String[] tokens = side.split(":", -1);
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.contains(".")) {
                if (i != tokens.length - 1) {
                    throw new IocFormatException("內嵌 IPv4 只能在尾端:" + whole);
                }
                int[] octets = Ipv4Normalizer.octets(token);
                groups.add((octets[0] << 8) | octets[1]);
                groups.add((octets[2] << 8) | octets[3]);
            } else {
                groups.add(parseHexGroup(token, whole));
            }
        }
        return groups;
    }

    private static int parseHexGroup(String token, String whole) {
        if (token.isEmpty() || token.length() > 4) {
            throw new IocFormatException("IPv6 組必須是 1–4 位十六進位:" + whole);
        }
        try {
            return Integer.parseInt(token, 16);
        } catch (NumberFormatException e) {
            throw new IocFormatException("IPv6 組含非十六進位字元:" + whole);
        }
    }

    /** 回傳 {起點, 長度};無零組時長度 0。相同長度取最左(RFC 5952 §4.2.3)。 */
    private static int[] longestZeroRun(int[] groups) {
        int bestStart = -1;
        int bestLength = 0;
        int start = -1;
        for (int i = 0; i <= GROUPS; i++) {
            if (i < GROUPS && groups[i] == 0) {
                if (start < 0) {
                    start = i;
                }
                continue;
            }
            if (start >= 0 && i - start > bestLength) {
                bestStart = start;
                bestLength = i - start;
            }
            start = -1;
        }
        return new int[] {bestStart, bestLength};
    }

    private static String joinHex(int[] groups, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (i > from) {
                sb.append(':');
            }
            sb.append(Integer.toHexString(groups[i]));
        }
        return sb.toString();
    }
}
