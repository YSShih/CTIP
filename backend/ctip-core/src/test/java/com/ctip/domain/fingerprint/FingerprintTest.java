package com.ctip.domain.fingerprint;

import static com.ctip.testing.IndicatorTestBuilder.DEMO_TENANT;
import static com.ctip.testing.IndicatorTestBuilder.SOURCE_A;
import static com.ctip.testing.IndicatorTestBuilder.report;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.domain.indicator.HashRecord;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.indicator.IocValue;
import com.ctip.domain.indicator.NewIndicatorCommand;
import com.ctip.domain.source.Reputation;
import com.ctip.sdk.FingerprintAlgorithm;
import com.ctip.sdk.IocType;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 指紋策略與去重指紋物化(docs/spec/07-domain-intel.md §7.4):
 * SHA-256(normalizedValue) 的 64 字元小寫十六進位;指紋不是識別鍵。
 */
@Tag("unit")
class FingerprintTest {

    private final Sha256FingerprintStrategy strategy = new Sha256FingerprintStrategy();

    @Test
    void computesKnownSha256Vector() {
        assertThat(strategy.fingerprint("abc").hex())
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(strategy.algorithm()).isEqualTo(FingerprintAlgorithm.SHA256);
    }

    @Test
    void fingerprintIsComputedOnNormalizedValueNotRawValue() {
        IocValue value =
                new IocValue(IocType.DOMAIN, null, "MAL-Example.CTIP-Sample.NET.", "mal-example.ctip-sample.net");
        Indicator indicator = Indicator.create(command(value), strategy);
        // 不變量 I2:對 normalizedValue 計算,絕不對原始值計算
        assertThat(indicator.fingerprint())
                .isEqualTo(strategy.fingerprint("mal-example.ctip-sample.net"))
                .isNotEqualTo(strategy.fingerprint("MAL-Example.CTIP-Sample.NET."));
    }

    @Test
    void createMaterializesPlatformHashRecord() {
        IocValue value =
                new IocValue(IocType.DOMAIN, null, "mal-example.ctip-sample.net", "mal-example.ctip-sample.net");
        Indicator indicator = Indicator.create(command(value), strategy);
        // hash_records 寫入(04 表 6):sourceId = null 表示平台計算
        assertThat(indicator.snapshot().hashRecords())
                .containsExactly(new HashRecord(
                        FingerprintAlgorithm.SHA256, indicator.fingerprint().hex(), null));
    }

    @Test
    void missingAlgorithmIsTranslatedToIllegalState() {
        assertThatThrownBy(() -> Sha256FingerprintStrategy.digestFor("NO-SUCH-ALGO"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fingerprintValueObjectRejectsInvalidHex() {
        assertThatThrownBy(() -> new Fingerprint("XYZ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Fingerprint("ba7816")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Fingerprint(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hashRecordRejectsUppercaseOrNonHexDigest() {
        assertThatThrownBy(() -> new HashRecord(FingerprintAlgorithm.SHA256, "ABCDEF", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HashRecord(FingerprintAlgorithm.SHA256, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static NewIndicatorCommand command(IocValue value) {
        return new NewIndicatorCommand(
                new IndicatorId(UUID.fromString("00000000-0000-0000-0000-00000000beef")),
                DEMO_TENANT,
                value,
                report(SOURCE_A).build(),
                new Reputation(50));
    }
}
