package com.ctip.testing;

import com.ctip.application.port.PasswordHasherPort;
import com.ctip.domain.user.PasswordHash;

/**
 * 測試用密碼雜湊:產出符合 BCrypt cost 12 格式的可逆字串(不變量 U3 由 PasswordHash 檢查格式)。
 * 不做真實 BCrypt——單元測試不該為每次註冊付出 2^12 輪的成本。
 */
public final class FakePasswordHasher implements PasswordHasherPort {

    private static final String PREFIX = "$2a$12$";

    @Override
    public PasswordHash hash(String rawPassword) {
        return new PasswordHash(PREFIX + Integer.toHexString(rawPassword.hashCode()) + "fake");
    }

    @Override
    public boolean matches(String rawPassword, PasswordHash hash) {
        return hash(rawPassword).equals(hash);
    }
}
