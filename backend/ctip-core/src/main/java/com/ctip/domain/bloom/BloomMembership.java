package com.ctip.domain.bloom;

import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorStatus;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.Tlp;

/**
 * 兩層 Bloom 的成員條件(docs/spec/11-sync-bloom.md §11.2,<strong>強制</strong>)。
 *
 * <table>
 *   <caption>成員條件</caption>
 *   <tr><th>Bloom</th><th>條件</th></tr>
 *   <tr><td>public</td><td>owner = PUBLIC ∧ tlp = CLEAR ∧ status = ACTIVE ∧ 非全部來源 INTERNAL_ONLY</td></tr>
 *   <tr><td>tenant</td><td>owner = 該 tenant ∧ tlp ∈ {AMBER, AMBER_STRICT} ∧ status = ACTIVE</td></tr>
 * </table>
 *
 * <p>⚠️ <strong>tenant 層刻意不含再散布條件</strong>(ADR 0019):手動提交的來源政策固定
 * {@code INTERNAL_ONLY},若沿用 {@link Indicator#eligibleForBloom()},tenant bloom 會恆為空。
 * §11.2 的 tenant 條件本來就沒有再散布條件——私有 Bloom 只發給該租戶自己,不涉及再散布。
 *
 * <p>兩個述詞在資料庫端另有等價的 SQL 實作(成員掃描不 hydrate 聚合);
 * {@code BloomCoverageTest} 逐筆比對兩者,防止其中一邊被改動後悄悄漂移。
 */
public final class BloomMembership {

    private BloomMembership() {}

    /** public bloom:委派給聚合自身的 L7 判定。 */
    public static boolean inPublicBloom(Indicator indicator) {
        return indicator.ownerTenantId().isPublic() && indicator.eligibleForBloom();
    }

    /** tenant bloom:只含該租戶自己的 AMBER / AMBER_STRICT 且 ACTIVE 的 IOC。 */
    public static boolean inTenantBloom(Indicator indicator, TenantId owner) {
        Tlp tlp = indicator.tlp();
        return !owner.isPublic()
                && indicator.ownerTenantId().equals(owner)
                && indicator.status() == IndicatorStatus.ACTIVE
                && (tlp == Tlp.AMBER || tlp == Tlp.AMBER_STRICT);
    }
}
