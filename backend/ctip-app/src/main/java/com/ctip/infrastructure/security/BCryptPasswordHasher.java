package com.ctip.infrastructure.security;

import com.ctip.application.port.PasswordHasherPort;
import com.ctip.domain.user.PasswordHash;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * PasswordHasherPort 的 BCrypt 實作(§10.4:cost 12)。
 * bean 由 {@code SecurityConfig} 建立——infrastructure 不得反向依賴 config(ArchUnit 規則 5)。
 */
public class BCryptPasswordHasher implements PasswordHasherPort {

    private final PasswordEncoder encoder;

    public BCryptPasswordHasher(PasswordEncoder encoder) {
        this.encoder = encoder;
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
