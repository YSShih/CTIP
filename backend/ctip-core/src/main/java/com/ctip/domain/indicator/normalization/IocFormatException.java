package com.ctip.domain.indicator.normalization;

/** 正規化失敗:格式驗證不通過(拒絕規則 MALFORMED_VALUE 的來源;docs/spec/07-domain-intel.md §7.3)。 */
public final class IocFormatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IocFormatException(String message) {
        super(message);
    }
}
