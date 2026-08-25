package com.ctip.domain.indicator.normalization;

import com.ctip.sdk.IocType;

/** IPV4:驗證後轉標準點分十進位,去除前導零(docs/spec/07-domain-intel.md §7.2)。 */
final class Ipv4Normalizer implements IocNormalizer {

    @Override
    public IocType type() {
        return IocType.IPV4;
    }

    @Override
    public String normalize(String cleanedValue) {
        int[] octets = octets(cleanedValue);
        return octets[0] + "." + octets[1] + "." + octets[2] + "." + octets[3];
    }

    /** 解析四個十進位 octet(1–3 位數、0–255,允許前導零);格式不合丟 IocFormatException。 */
    static int[] octets(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            throw new IocFormatException("IPv4 必須有四段:" + value);
        }
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            octets[i] = parseOctet(parts[i], value);
        }
        return octets;
    }

    private static int parseOctet(String part, String whole) {
        if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) {
            throw new IocFormatException("IPv4 octet 格式不合:" + whole);
        }
        int octet = Integer.parseInt(part);
        if (octet > 255) {
            throw new IocFormatException("IPv4 octet 超出 0–255:" + whole);
        }
        return octet;
    }
}
