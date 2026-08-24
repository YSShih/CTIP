package com.ctip.domain.indicator;

import com.ctip.domain.source.SourceId;
import com.ctip.sdk.FingerprintAlgorithm;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 去重指紋記錄(docs/spec/04-data-dictionary.md 表 6)。
 * 存的是平台去重指紋(FingerprintAlgorithm),與 IocValue.hashType(IOC 是檔案雜湊)是兩件不同的事。
 * sourceId 為 null 表示平台計算。
 */
public record HashRecord(FingerprintAlgorithm algorithm, String digest, SourceId sourceId) {

    private static final Pattern HEX = Pattern.compile("^[0-9a-f]+$");

    public HashRecord {
        Objects.requireNonNull(algorithm, "algorithm 不得為 null");
        if (digest == null || !HEX.matcher(digest).matches()) {
            throw new IllegalArgumentException("digest 必須為小寫十六進位");
        }
    }
}
