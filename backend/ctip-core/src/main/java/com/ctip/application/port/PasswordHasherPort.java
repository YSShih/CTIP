package com.ctip.application.port;

import com.ctip.domain.user.PasswordHash;

/**
 * 密碼雜湊與比對(docs/spec/10-identity-plans.md §10.4:BCrypt cost 12 或 Argon2id)。
 * 實作在 infrastructure——core 不得依賴 Spring Security(ArchUnit 規則 1)。
 */
public interface PasswordHasherPort {

    PasswordHash hash(String rawPassword);

    boolean matches(String rawPassword, PasswordHash hash);

    /**
     * 一個固定的、不屬於任何使用者的雜湊,供「帳號不存在」時做等時比對。
     *
     * <p>沒有它的話,不存在的帳號會略過 BCrypt 而在數毫秒內回應,已存在的帳號則要數百毫秒——
     * 回應時間本身就洩漏了帳號是否註冊,錯誤訊息寫得再一致也沒用(實測 7ms vs 440ms)。
     * 演算法參數由實作決定,故 dummy 也由實作提供。
     */
    PasswordHash dummyHash();
}
