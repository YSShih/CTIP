package com.ctip.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** 端點類別的比例上限(docs/spec/10-identity-plans.md §10.7;ADR 0020:read 100% / write 20% / heavy 5%)。 */
@Tag("unit")
class EndpointClassTest {

    @Test
    void sharesAreTheSpecifiedPercentagesOfThePlanTotal() {
        QuotaLimit total = QuotaLimit.of(1200L);
        assertThat(EndpointClass.READ.shareOf(total).orElse(0)).isEqualTo(1200);
        assertThat(EndpointClass.WRITE.shareOf(total).orElse(0)).isEqualTo(240);
        assertThat(EndpointClass.HEAVY.shareOf(total).orElse(0)).isEqualTo(60);
    }

    /** 比例取整後為 0 會把「有配額」變成「完全不能用」——分類上限至少 1。 */
    @Test
    void tinyQuotasKeepAtLeastOneRequest() {
        assertThat(EndpointClass.HEAVY.shareOf(QuotaLimit.of(3L)).orElse(0)).isEqualTo(1);
    }

    /** 分類上限恆低於(或等於)總上限——這是 ADR 0020 選擇比例而非另設欄位的理由。 */
    @Test
    void shareNeverExceedsTheTotal() {
        QuotaLimit total = QuotaLimit.of(60L);
        for (EndpointClass endpointClass : EndpointClass.values()) {
            assertThat(endpointClass.shareOf(total).orElse(0)).isLessThanOrEqualTo(60);
        }
    }

    /** ENTERPRISE 的 requests_per_day 為 null(無上限);比例套在「沒有數字」上仍是沒有數字。 */
    @Test
    void unlimitedAndDisabledPassThrough() {
        assertThat(EndpointClass.HEAVY.shareOf(QuotaLimit.unlimited()).isUnlimited())
                .isTrue();
        assertThat(EndpointClass.WRITE.shareOf(QuotaLimit.disabled()).isDisabled())
                .isTrue();
    }

    @Test
    void keySegmentIsTheLowercaseName() {
        assertThat(EndpointClass.HEAVY.keySegment()).isEqualTo("heavy");
    }
}
