package com.ctip.infrastructure.security;

import com.ctip.application.port.SecureTokenGeneratorPort;
import java.security.SecureRandom;

/** 以 {@link SecureRandom} 產生 base62 字串(refresh token 原文、API key 隨機段)。 */
public class SecureRandomTokenGenerator implements SecureTokenGeneratorPort {

    private static final char[] ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    private final SecureRandom random = new SecureRandom();

    @Override
    public String randomBase62(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("length 必須為正");
        }
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return builder.toString();
    }
}
