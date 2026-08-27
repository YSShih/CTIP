package com.ctip.domain.user;

/**
 * 使用者輸入的密碼原文。只存在於註冊／登入／改密碼的呼叫堆疊中,
 * 絕不持久化、絕不寫入日誌(安全測試條號 8)。
 *
 * <p>§10.4 未定義最小長度;依 §0.4 優先序(安全性最先)取 12 碼為硬性下限
 * (ADR 0012 決策 6)。
 */
public record RawPassword(String value) {

    public static final int MIN_LENGTH = 12;
    private static final int MAX_LENGTH = 256;

    public RawPassword {
        if (value == null || value.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("密碼長度至少 " + MIN_LENGTH + " 碼");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("密碼長度不得超過 " + MAX_LENGTH + " 碼");
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException("密碼不得為空白");
        }
    }

    /** 避免不慎將原文寫入日誌或錯誤訊息。 */
    @Override
    public String toString() {
        return "RawPassword[REDACTED]";
    }
}
