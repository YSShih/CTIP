package com.ctip.application.port;

/**
 * 密碼學安全亂數字串(refresh token 原文、API key 隨機段)。
 * domain 不得取亂數(ArchUnit 規則 9),application 亦透過此 port 取得。
 */
public interface SecureTokenGeneratorPort {

    /** 產生指定長度的 base62 字串。 */
    String randomBase62(int length);
}
