package com.ctip.domain.notification;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * 送達簽章的規範化組成(docs/spec/13-platform-ops.md §13.2)。
 *
 * <p>簽章對象是 {@code timestamp + "." + body},不是原始 body——§13.2 同一節內兩句互斥,
 * [ADR 0021](../architecture/decisions/0021-phase20-23-spec-resolutions.md) 第 1 節定調取前者:
 * 只有它防得了重放。timestamp 為 epoch 秒,與送達標頭 {@code X-CTIP-Timestamp} 相同。
 */
public final class WebhookSignature {

    /** 接收端應拒絕偏差超過此值的請求(§13.2,已寫入 {@code docs/api/webhooks.md})。 */
    public static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

    private static final String PREFIX = "sha256=";

    private WebhookSignature() {}

    /** epoch 秒 + {@code "."} + body 的位元組串接。 */
    public static byte[] payload(long epochSecond, byte[] body) {
        byte[] prefix = (epochSecond + ".").getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, payload, 0, prefix.length);
        System.arraycopy(body, 0, payload, prefix.length, body.length);
        return payload;
    }

    /** {@code X-CTIP-Signature} 的標頭值,格式 {@code sha256=<hex>}。 */
    public static String header(String hex) {
        return PREFIX + hex;
    }

    /** 接收端的時鐘偏差判定(§13.2:超過 5 分鐘應拒絕)。此處供測試與文件引用同一個判準。 */
    public static boolean withinClockSkew(Instant signedAt, Instant now) {
        return Duration.between(signedAt, now).abs().compareTo(MAX_CLOCK_SKEW) <= 0;
    }
}
