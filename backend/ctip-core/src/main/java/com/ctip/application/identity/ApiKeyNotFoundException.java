package com.ctip.application.identity;

/** 找不到 API key,或該 key 屬於其他租戶(§9.4:跨租戶一律回 404,不回 403)。 */
public class ApiKeyNotFoundException extends RuntimeException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public ApiKeyNotFoundException(String message) {
        super(message);
    }
}
