package com.ctip.domain.indicator;

import com.ctip.sdk.IocHashType;
import com.ctip.sdk.IocType;
import java.util.Objects;

/**
 * IOC 值物件:原始值 + 正規化後的 canonical 值(docs/spec/02-ddd-model.md §2.6)。
 * 不變量 I3:hashType 非 null ⟺ type = FILE_HASH。
 */
public record IocValue(IocType type, IocHashType hashType, String raw, String normalized) {

    private static final int MAX_LENGTH = 2048;

    public IocValue {
        Objects.requireNonNull(type, "type 不得為 null");
        Objects.requireNonNull(raw, "raw 不得為 null");
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException("normalized 不得為空");
        }
        if (raw.length() > MAX_LENGTH || normalized.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("IOC 值長度超過 " + MAX_LENGTH);
        }
        if ((type == IocType.FILE_HASH) != (hashType != null)) {
            throw new IllegalArgumentException("hashType 非 null ⟺ type = FILE_HASH(不變量 I3)");
        }
    }
}
