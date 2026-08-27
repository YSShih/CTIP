package com.ctip.application.identity;

/** 登入失敗原因。對外一律映射為同一則 401 訊息,不揭露差異(避免帳號列舉)。 */
public enum LoginFailure {
    INVALID_CREDENTIALS,
    LOCKED
}
