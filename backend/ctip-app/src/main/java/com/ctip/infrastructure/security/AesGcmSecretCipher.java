package com.ctip.infrastructure.security;

import com.ctip.application.port.SecretCipherPort;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM 的 {@link SecretCipherPort} 實作(不變量 W2 定調;ADR 0021)。
 *
 * <p>密文格式:{@code nonce(12 bytes) || ciphertext || tag(16 bytes)}。nonce 每次重新取,
 * <strong>絕不重複使用</strong>——GCM 在同一把金鑰下重用 nonce 會直接洩漏明文異或值。
 *
 * <p>金鑰來自 {@code WEBHOOK_SECRET_KEK};任意長度的字串以 SHA-256 導出 32 bytes 的 AES 金鑰,
 * 讓運維端可以直接放 secret manager 給的字串,不必自己湊長度。
 */
public class AesGcmSecretCipher implements SecretCipherPort {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmSecretCipher(String keyEncryptionKey) {
        if (keyEncryptionKey == null || keyEncryptionKey.isBlank()) {
            throw new IllegalArgumentException("WEBHOOK_SECRET_KEK 不得為空");
        }
        this.key = new SecretKeySpec(sha256(keyEncryptionKey), "AES");
    }

    @Override
    public byte[] encrypt(String plaintext) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[nonce.length + sealed.length];
            System.arraycopy(nonce, 0, out, 0, nonce.length);
            System.arraycopy(sealed, 0, out, nonce.length, sealed.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("webhook 密鑰加密失敗", e);
        }
    }

    @Override
    public String decrypt(byte[] ciphertext) {
        if (ciphertext == null || ciphertext.length <= NONCE_BYTES) {
            throw new IllegalArgumentException("密文長度不足");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    new GCMParameterSpec(TAG_BITS, Arrays.copyOfRange(ciphertext, 0, NONCE_BYTES)));
            byte[] plain = cipher.doFinal(Arrays.copyOfRange(ciphertext, NONCE_BYTES, ciphertext.length));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // 認證標籤不符也走這裡:換過 KEK 或密文被竄改,兩者都不該把細節寫進訊息
            throw new IllegalStateException("webhook 密鑰解密失敗(WEBHOOK_SECRET_KEK 是否換過?)", e);
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
