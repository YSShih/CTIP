package com.ctip.application.stix;

import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.tenant.TenantId;
import org.springframework.stereotype.Service;

/**
 * 再散布政策的輸出過濾單點(docs/spec/07-domain-intel.md §7.9 規則 2:
 * 邏輯必須集中於一個 RedistributionFilter,不得散落各 controller)。
 * 作用域(§7.9 修正):只作用於跨租戶與公開輸出——viewer 即擁有租戶時不套用,
 * 租戶對自己的資料看得到全貌。M1 實作規則 3(全 INTERNAL_ONLY 不得對非擁有租戶輸出,
 * 委派 domain 的 I14);規則 4(attribution)與規則 5(DERIVED_ONLY 遮罩)於 Phase 9
 * 的 DTO 映射在此擴充。
 */
@Service
public class RedistributionFilter {

    /** 規則 3:此 indicator 是否可對 viewer 輸出(擁有租戶恆可見)。 */
    public boolean redistributableTo(Indicator indicator, TenantId viewer) {
        return indicator.canBeRedistributedTo(viewer);
    }
}
