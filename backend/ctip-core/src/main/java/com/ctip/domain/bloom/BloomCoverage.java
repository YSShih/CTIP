package com.ctip.domain.bloom;

import java.util.List;

/**
 * 每一層 Bloom 覆蓋了什麼、以及<strong>什麼完全沒有覆蓋</strong>
 * (docs/spec/11-sync-bloom.md §11.1、§11.5)。
 *
 * <p>manifest 的 {@code coverage} 與 {@code notCovered} 是<strong>必填</strong>——
 * §11.5 的理由是「client 開發者必須在 manifest 就看到覆蓋範圍限制」。
 * 沒有這兩個欄位,「miss 代表安全」這個錯誤結論在 client 端幾乎必然發生:
 * public Bloom 只含 {@code TLP:CLEAR},而 {@code TLP:GREEN} 沒有<strong>任何</strong> Bloom 覆蓋。
 *
 * <p>文字內容放 domain 而非 DTO,是因為它描述的是成員條件({@link BloomMembership})本身;
 * 兩者改動必須同步,分開放會讓 manifest 講的覆蓋範圍與實際成員條件安靜地漂移。
 */
public final class BloomCoverage {

    /** 沒有任何 Bloom 覆蓋的 TLP 等級(§11.1:GREEN 不得放上公開通道,也不屬任何租戶的私有集合)。 */
    public static final List<String> NOT_COVERED = List.of("TLP:GREEN");

    private static final String PUBLIC = "TLP:CLEAR only";
    private static final String TENANT = "TLP:AMBER, TLP:AMBER_STRICT of your tenant";

    private BloomCoverage() {}

    public static String describe(BloomScope scope) {
        return switch (scope) {
            case PUBLIC -> PUBLIC;
            case TENANT -> TENANT;
        };
    }
}
