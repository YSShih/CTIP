package com.ctip.infrastructure.web;

/**
 * 請求本文上限的單一出處。
 *
 * <p>{@link RequestBodySizeLimitFilter}(容器層,資料進堆積之前)與
 * {@code IocWriteController}(端點層,防止 filter 未註冊時完全沒有上限)必須用同一個數字
 * ——兩處各寫一份的話,調整其中一個就會出現「filter 放行、controller 才回 413」這種
 * 明明擋掉了卻已經吃掉一整份記憶體的狀態。
 */
public final class RequestBodySizeLimits {

    /** ENTERPRISE 的 500,000 列 CSV 約 30 MB,留兩倍餘裕(§9.7)。 */
    public static final int MAX_IMPORT_BYTES = 64 * 1024 * 1024;

    private RequestBodySizeLimits() {}
}
