package com.ctip.application.port;

/**
 * 對稱加解密(目前唯一的使用者是 webhook 簽章密鑰)。
 *
 * <p>不變量 W2 定調(ADR 0021):webhook secret 必須<strong>可還原</strong>才算得出送達簽章,
 * 因此以 AES-GCM 加密儲存而非雜湊。這與 refresh token／API key 只存雜湊的作法不同,
 * 因為那兩者是<strong>驗證</strong>(比對即可),簽章是<strong>產生</strong>(必須持有原文)。
 * 金鑰來自 {@code WEBHOOK_SECRET_KEK}。
 */
public interface SecretCipherPort {

    byte[] encrypt(String plaintext);

    String decrypt(byte[] ciphertext);
}
