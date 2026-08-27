package com.ctip.infrastructure.security;

import com.ctip.application.port.PasswordHasherPort;
import com.ctip.domain.user.PasswordHash;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * PasswordHasherPort 的 BCrypt 實作(§10.4:cost 12)。
 * bean 由 {@code SecurityConfig} 建立——infrastructure 不得反向依賴 config(ArchUnit 規則 5)。
 */
public class BCryptPasswordHasher implements PasswordHasherPort {

    /**
     * 等時比對用的固定明文。它只被雜湊一次、永不對應任何帳號;
     * 公開它不影響安全性——真正的成本在 BCrypt 本身,而每次啟動的 salt 都不同。
     */
    private static final String TIMING_EQUALISATION_PLAINTEXT = "ctip-dummy-password-for-timing-equalisation";

    private final PasswordEncoder encoder;
    private final PasswordHash dummyHash;

    public BCryptPasswordHasher(PasswordEncoder encoder) {
        this.encoder = encoder;
        // 啟動時算一次(約 250ms),之後每次「帳號不存在」的登入都用它耗掉等量時間
        this.dummyHash = new PasswordHash(encoder.encode(TIMING_EQUALISATION_PLAINTEXT));
    }

    @Override
    public PasswordHash dummyHash() {
        return dummyHash;
    }

    @Override
    public PasswordHash hash(String rawPassword) {
        return new PasswordHash(encoder.encode(rawPassword));
    }

    @Override
    public boolean matches(String rawPassword, PasswordHash hash) {
        return encoder.matches(rawPassword, hash.value());
    }
}
