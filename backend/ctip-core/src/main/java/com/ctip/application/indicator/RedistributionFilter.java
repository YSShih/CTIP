package com.ctip.application.indicator;

import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorSourceSnapshot;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.RedistributionPolicy;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 再散布政策的輸出過濾單點(docs/spec/07-domain-intel.md §7.9 規則 2:
 * 邏輯必須集中於一個 RedistributionFilter,不得散落各 controller)。
 * 作用域(§7.9 修正):只作用於跨租戶與公開輸出——viewer 即擁有租戶時不套用,
 * 租戶對自己的資料看得到全貌。
 * 規則 3(全 INTERNAL_ONLY 不對非擁有租戶輸出)委派 domain 的 I14;
 * 規則 4(ATTRIBUTION_REQUIRED 附標註)與規則 5(DERIVED_ONLY 不得回傳來源明細)在此判定,
 * DTO 映射依判定結果組裝(docs/spec/09-api.md §9.5 輸出過濾第 4 步)。
 */
@Service
public class RedistributionFilter {

    /** 規則 3:此 indicator 是否可對 viewer 輸出(擁有租戶恆可見)。 */
    public boolean redistributableTo(Indicator indicator, TenantId viewer) {
        return indicator.canBeRedistributedTo(viewer);
    }

    /**
     * 規則 5:viewer 可見的來源明細。擁有租戶看全部;跨租戶/公開輸出只回
     * PUBLIC_REDISTRIBUTABLE 與 ATTRIBUTION_REQUIRED 的來源記錄
     * (DERIVED_ONLY 不得回傳來源明細與原始記錄、INTERNAL_ONLY 不得輸出)。
     */
    public List<IndicatorSourceSnapshot> visibleSourceRecords(Indicator indicator, TenantId viewer) {
        List<IndicatorSourceSnapshot> records = indicator.snapshot().sources();
        if (indicator.ownerTenantId().equals(viewer) && !viewer.isPublic()) {
            return records; // 擁有租戶(非 public)看得到全貌;public 無成員,匿名屬公開輸出
        }
        return records.stream()
                .filter(r -> isDisclosable(r.redistributionPolicy()))
                .toList();
    }

    /** 規則 4:必須附上來源標註(attribution)的來源記錄。 */
    public List<IndicatorSourceSnapshot> attributionRequired(Indicator indicator, TenantId viewer) {
        return visibleSourceRecords(indicator, viewer).stream()
                .filter(r -> r.redistributionPolicy() == RedistributionPolicy.ATTRIBUTION_REQUIRED)
                .toList();
    }

    private static boolean isDisclosable(RedistributionPolicy policy) {
        return policy == RedistributionPolicy.PUBLIC_REDISTRIBUTABLE
                || policy == RedistributionPolicy.ATTRIBUTION_REQUIRED;
    }
}
