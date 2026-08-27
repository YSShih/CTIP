package com.ctip.application.port;

import com.ctip.domain.user.PasswordHash;

/**
 * 密碼雜湊與比對(docs/spec/10-identity-plans.md §10.4:BCrypt cost 12 或 Argon2id)。
 * 實作在 infrastructure——core 不得依賴 Spring Security(ArchUnit 規則 1)。
 */
public interface PasswordHasherPort {

    PasswordHash hash(String rawPassword);

    boolean matches(String rawPassword, PasswordHash hash);
}
