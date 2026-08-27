package com.ctip.domain.user;

/**
 * 密碼雜湊。不變量 U3:只接受 BCrypt(cost ≥ 12)或 Argon2id 的輸出,絕不儲存原文
 * (docs/spec/10-identity-plans.md §10.4)。實際雜湊由 PasswordHasherPort 於 infrastructure 完成。
 */
public record PasswordHash(String value) {

    private static final int BCRYPT_MIN_COST = 12;

    public PasswordHash {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("passwordHash 不得為空");
        }
        // Argon2id 的參數由產生端(PasswordHasherPort)決定,此處只認格式
        if (!value.startsWith("$argon2id$")) {
            requireBcryptWithMinimumCost(value);
        }
    }

    private static void requireBcryptWithMinimumCost(String value) {
        if (!value.startsWith("$2a$") && !value.startsWith("$2b$") && !value.startsWith("$2y$")) {
            throw new IllegalArgumentException("passwordHash 必須為 BCrypt 或 Argon2id 輸出(不變量 U3)");
        }
        int cost;
        try {
            cost = Integer.parseInt(value.substring(4, 6));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("passwordHash 的 BCrypt cost 無法解析(不變量 U3)");
        }
        if (cost < BCRYPT_MIN_COST) {
            throw new IllegalArgumentException("BCrypt cost 必須 >= " + BCRYPT_MIN_COST + "(不變量 U3):" + cost);
        }
    }
}
