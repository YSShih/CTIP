package com.ctip.domain.indicator.normalization;

import com.ctip.sdk.IocType;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * URL:scheme 與 host 小寫、移除預設 port(http:80 / https:443)、
 * 路徑百分比編碼正規化(大寫十六進位、解碼非保留字元)、query 依 key 排序、
 * 移除 fragment(docs/spec/07-domain-intel.md §7.2)。
 */
final class UrlNormalizer implements IocNormalizer {

    private static final String UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
    private static final String HEX = "0123456789ABCDEF";

    @Override
    public IocType type() {
        return IocType.URL;
    }

    @Override
    public String normalize(String cleanedValue) {
        URI uri = parse(cleanedValue);
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = DomainNormalizer.toAscii(uri.getHost()).toLowerCase(Locale.ROOT);
        int port = uri.getPort() == defaultPort(scheme) ? -1 : uri.getPort();
        String path = normalizePercent(uri.getRawPath() == null ? "" : uri.getRawPath());
        if (path.isEmpty()) {
            path = "/";
        }
        String query = sortQuery(uri.getRawQuery());
        StringBuilder sb = new StringBuilder(scheme).append("://").append(host);
        if (port >= 0) {
            sb.append(':').append(port);
        }
        sb.append(path);
        if (query != null && !query.isEmpty()) {
            sb.append('?').append(query);
        }
        return sb.toString();
    }

    private static URI parse(String value) {
        try {
            URI uri = new URI(value);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IocFormatException("URL 必須含 scheme 與 host:" + value);
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IocFormatException("URL 格式不合:" + value);
        }
    }

    private static int defaultPort(String scheme) {
        return switch (scheme) {
            case "http" -> 80;
            case "https" -> 443;
            default -> -1;
        };
    }

    /** 百分比編碼正規化:解碼非保留字元、其餘統一大寫十六進位。 */
    static String normalizePercent(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '%') {
                sb.append(c);
                continue;
            }
            if (i + 2 >= raw.length()) {
                throw new IocFormatException("百分比編碼不完整:" + raw);
            }
            int decoded = decodeHexPair(raw.charAt(i + 1), raw.charAt(i + 2), raw);
            if (UNRESERVED.indexOf(decoded) >= 0) {
                sb.append((char) decoded);
            } else {
                sb.append('%').append(HEX.charAt(decoded >> 4)).append(HEX.charAt(decoded & 0x0F));
            }
            i += 2;
        }
        return sb.toString();
    }

    private static int decodeHexPair(char high, char low, String whole) {
        int hi = Character.digit(high, 16);
        int lo = Character.digit(low, 16);
        if (hi < 0 || lo < 0) {
            throw new IocFormatException("百分比編碼含非十六進位字元:" + whole);
        }
        return (hi << 4) | lo;
    }

    /** query 參數依 key 穩定排序;同 key 保留原相對順序。 */
    static String sortQuery(String rawQuery) {
        if (rawQuery == null) {
            return null;
        }
        return Arrays.stream(rawQuery.split("&", -1))
                .filter(param -> !param.isEmpty())
                .sorted(Comparator.comparing(param -> {
                    int eq = param.indexOf('=');
                    return eq < 0 ? param : param.substring(0, eq);
                }))
                .collect(Collectors.joining("&"));
    }
}
