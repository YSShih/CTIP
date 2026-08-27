package com.ctip.testing;

import com.ctip.application.port.SecureTokenGeneratorPort;

/** 測試用確定性亂數來源:第 n 次呼叫產生可預期且互異的 base62 字串。 */
public final class SequentialTokenGenerator implements SecureTokenGeneratorPort {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private int counter;

    @Override
    public String randomBase62(int length) {
        counter++;
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(ALPHABET.charAt((counter * 31 + i * 7) % ALPHABET.length()));
        }
        return builder.toString();
    }
}
