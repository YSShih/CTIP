package com.ctip.testing;

import com.ctip.application.port.PasswordHasherPort;
import com.ctip.domain.user.PasswordHash;

/**
 * 測試用密碼雜湊:產出符合 BCrypt cost 12 格式的可逆字串(不變量 U3 由 PasswordHash 檢查格式)。
 * 不做真實 BCrypt——單元測試不該為每次註冊付出 2^12 輪的成本。
 */
public final class FakePasswordHasher implements PasswordHasherPort {

    private static final String PREFIX = "$2a$12$";

    private int comparisons;

    /** 比對次數。等時比對的斷言靠它:帳號不存在時也必須發生一次比對。 */
    public int comparisons() {
        return comparisons;
    }

    @Override
    public PasswordHash hash(String rawPassword) {
        return new PasswordHash(PREFIX + Integer.toHexString(rawPassword.hashCode()) + "fake");
    }

    @Override
    public boolean matches(String rawPassword, PasswordHash hash) {
        comparisons++;
        return hash(rawPassword).equals(hash);
    }

    @Override
    public PasswordHash dummyHash() {
        return hash("dummy-never-matches-any-account");
    }
}
