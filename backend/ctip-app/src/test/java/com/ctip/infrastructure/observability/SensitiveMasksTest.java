package com.ctip.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** 遮罩規則(docs/spec/13-platform-ops.md §13.6「絕不記錄」)。 */
@Tag("unit")
class SensitiveMasksTest {

    @Test
    void bearerTokensAndJwtsAreMasked() {
        String masked = SensitiveMasks.apply("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2ln");

        assertThat(masked).doesNotContain("eyJhbGciOiJIUzI1NiJ9");
        assertThat(masked).contains(SensitiveMasks.MASK);
    }

    @Test
    void apiKeysKeepTheirEnvironmentPrefixOnly() {
        String masked = SensitiveMasks.apply("key=ctip_prod_0123456789abcdefABCDEF0123456789");

        assertThat(masked).contains("ctip_prod_" + SensitiveMasks.MASK);
        assertThat(masked).doesNotContain("0123456789abcdefABCDEF0123456789");
    }

    @Test
    void opaqueBase62TokensAreMasked() {
        String refreshToken = "aB3dE5gH7jK9mN1pQ3sT5vW7yZ9bD1fH3jL5nP7rT9vX1zC3";

        assertThat(SensitiveMasks.apply("token " + refreshToken)).doesNotContain(refreshToken);
    }

    /** 指紋與 traceId 是查問題的主線索,不得被遮掉——十六進位摘要沒有大寫字母。 */
    @Test
    void hexDigestsAndTraceIdsSurvive() {
        String digest = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        String traceId = "0af7651916cd43dd8448eb211c80319c";

        assertThat(SensitiveMasks.apply("fingerprint=" + digest)).contains(digest);
        assertThat(SensitiveMasks.apply("traceId=" + traceId)).contains(traceId);
    }

    @Test
    void passwordAndSecretFieldsAreMaskedInJsonAndQueryForm() {
        assertThat(SensitiveMasks.apply("{\"password\":\"hunter2-and-more\"}")).doesNotContain("hunter2-and-more");
        assertThat(SensitiveMasks.apply("webhookSecret=s3cret-value")).doesNotContain("s3cret-value");
        assertThat(SensitiveMasks.apply("X-API-Key: whatever-it-is")).doesNotContain("whatever-it-is");
    }

    @Test
    void nullAndEmptyInputsPassThrough() {
        assertThat(SensitiveMasks.apply(null)).isNull();
        assertThat(SensitiveMasks.apply("")).isEmpty();
        assertThat(SensitiveMasks.apply("nothing secret here")).isEqualTo("nothing secret here");
    }
}
