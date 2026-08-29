package com.ctip.domain.notification;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Webhook 簽章密鑰的<strong>原文</strong>(docs/spec/02-ddd-model.md §2.2 的值物件清單)。
 *
 * <p>不變量 W2 原寫「只存 secret 的 SHA-256」,但 §13.2 要求每次送達都以
 * {@code HMAC-SHA256(secret, …)} 簽章——只有摘要重建不出密鑰,兩者數學上互斥
 * ([ADR 0021](../architecture/decisions/0021-phase20-23-spec-resolutions.md) 第 3 節定調):
 * 密鑰改以 AES-GCM 加密儲存({@code webhooks.secret_encrypted}),
 * 對外契約(原文僅建立當下回傳一次)不變。加解密屬基礎設施,本型別只持有原文並計算簽章。
 *
 * <p>{@code toString()} 刻意不吐出原文:它會經過日誌、例外訊息與 debugger。
 */
public record HmacSecret(String value) {

    private static final String ALGORITHM = "HmacSHA256";

    /** §13.2 的密鑰長度未定;32 bytes 對應 HMAC-SHA256 的區塊安全度。 */
    public static final int RECOMMENDED_BYTES = 32;

    public HmacSecret {
        Objects.requireNonNull(value, "value 不得為 null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("webhook 簽章密鑰不得為空");
        }
    }

    /**
     * {@code HMAC-SHA256(secret, payload)} 的小寫 hex。
     *
     * <p>payload 的組成由 {@link WebhookSignature} 決定(§13.2:{@code timestamp + "." + body}
     * ——含 timestamp 才防得了重放;同節上一句的「簽章對象為原始 request body」已由 ADR 0021 廢止)。
     */
    public String hex(byte[] payload) {
        Objects.requireNonNull(payload, "payload 不得為 null");
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(value.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 不可用", e);
        }
    }

    @Override
    public String toString() {
        return "HmacSecret[redacted]";
    }
}
