package com.ctip.support;

import com.ctip.application.port.IndicatorRepository;
import com.ctip.domain.bloom.BloomBitArray;
import com.ctip.domain.bloom.BloomIndexer;
import com.ctip.domain.bloom.BloomParameters;
import com.ctip.domain.fingerprint.Fingerprint;
import com.ctip.domain.fingerprint.Sha256FingerprintStrategy;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.indicator.IndicatorSourceSnapshot;
import com.ctip.domain.indicator.IocValue;
import com.ctip.domain.indicator.NewIndicatorCommand;
import com.ctip.domain.indicator.SourceRecordStatus;
import com.ctip.domain.source.Reputation;
import com.ctip.domain.source.SourceId;
import com.ctip.sdk.Confidence;
import com.ctip.sdk.IocType;
import com.ctip.sdk.Severity;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Bloom 測試共用:建立可控的成員 fixture,以及「這個指紋在不在陣列裡」的探針。 */
public final class BloomFixtures {

    /** 遠在所有 fixture 的 validUntil 之後,用來把某筆 IOC 推入 EXPIRED(不變量 I6 要求已到期)。 */
    public static final Instant FAR_FUTURE = Instant.parse("2027-06-01T00:00:00Z");

    private static final Sha256FingerprintStrategy FINGERPRINTS = new Sha256FingerprintStrategy();

    private BloomFixtures() {}

    /** 固定 id 讓 fixture 冪等;{@code hexSuffix} 為最後一段的後 8 碼。 */
    public static IndicatorId id(String hexSuffix) {
        return new IndicatorId(UUID.fromString("00000000-0000-0000-0000-0000" + hexSuffix));
    }

    /** 與 {@link IndicatorFixtures} 相同的正規化值規則,因此指紋可在不查庫的情況下算出。 */
    public static Fingerprint fingerprintOf(String name) {
        return FINGERPRINTS.fingerprint(name + ".security.ctip-sample.net");
    }

    public static void expire(IndicatorRepository indicators, IndicatorId indicatorId) {
        Indicator indicator = indicators.findById(indicatorId).orElseThrow();
        indicator.markExpired(FAR_FUTURE);
        indicators.save(indicator);
    }

    public static void upsert(IndicatorRepository indicators, SourceId sourceId, IndicatorFixtures.Fixture fixture) {
        IndicatorFixtures.upsert(indicators, sourceId, fixture);
    }

    /**
     * 與 {@link #upsert} 相同,但可指定觀測時間。
     *
     * <p>delta 以 {@code last_seen} 為水位:比上一個版本更早的觀測時間不會被視為「新成員」,
     * 因此測 delta 一定要用晚於前一次生成的時間。
     */
    public static void upsertSeenAt(
            IndicatorRepository indicators, SourceId sourceId, IndicatorFixtures.Fixture fixture, Instant seen) {
        if (indicators.findById(fixture.id()).isPresent()) {
            return;
        }
        String normalized = fixture.name() + ".security.ctip-sample.net";
        IndicatorSourceSnapshot report = new IndicatorSourceSnapshot(
                sourceId,
                normalized,
                Confidence.of(60),
                Severity.MEDIUM,
                fixture.tlp(),
                seen,
                seen,
                null,
                fixture.policy(),
                1,
                SourceRecordStatus.ACTIVE,
                Set.of("bloom-test"),
                Map.of());
        indicators.save(Indicator.create(
                new NewIndicatorCommand(
                        fixture.id(),
                        fixture.owner(),
                        new IocValue(IocType.DOMAIN, null, normalized, normalized),
                        report,
                        new Reputation(70)),
                FINGERPRINTS));
    }

    /** Bloom 命中 = 全部 k 個索引都是 1。命中<strong>不代表</strong>確定存在(§11.1、不變量 L8)。 */
    public static boolean mightContain(BloomBitArray array, BloomParameters parameters, Fingerprint fingerprint) {
        return Arrays.stream(BloomIndexer.indices(fingerprint, parameters)).allMatch(array::get);
    }
}
