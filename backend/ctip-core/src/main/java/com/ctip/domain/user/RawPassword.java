package com.ctip.domain.user;

import java.nio.charset.StandardCharsets;

/**
 * 使用者輸入的密碼原文。只存在於註冊／登入／改密碼的呼叫堆疊中,
 * 絕不持久化、絕不寫入日誌(安全測試條號 8)。
 *
 * <p>§10.4 未定義最小長度;依 §0.4 優先序(安全性最先)取 12 碼為硬性下限
 * (ADR 0012 決策 6)。
 *
 * <p>上限是 <strong>UTF-8 位元組數</strong> 72 —— Spring Security 7 的 BCrypt 對超過 72 bytes 的
 * 輸入直接丟例外,不是靜默截斷。原本宣告 256 <em>字元</em>,於是一個 80 字元的密碼管理器密碼
 * 會在註冊時變成一則沒有欄位說明的 400(ADR 0013)。用位元組而非字元:25 個中文字就是 75 bytes。
 */
public record RawPassword(String value) {

    public static final int MIN_LENGTH = 12;

    /** BCrypt 的硬性上限:超過此值 Spring Security 7 會丟例外,而非靜默截斷。 */
    public static final int MAX_BYTES = 72;

    public RawPassword {
        if (value == null || value.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("密碼長度至少 " + MIN_LENGTH + " 碼");
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException("密碼不得為空白");
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("密碼不得超過 " + MAX_BYTES + " bytes(BCrypt 上限)");
        }
    }

    /** 避免不慎將原文寫入日誌或錯誤訊息。 */
    @Override
    public String toString() {
        return "RawPassword[REDACTED]";
    }
}
