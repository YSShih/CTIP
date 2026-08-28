package com.ctip.interfaces.rest;

import com.ctip.application.plan.QuotaService;
import com.ctip.config.CtipProperties;
import com.ctip.domain.tenant.TenantId;
import org.springframework.stereotype.Component;

/**
 * 「這個呼叫者一次能拿幾筆」——讀取端點的兩個尺寸配額(docs/spec/09-api.md §9.3、10 §10.6)。
 *
 * <p>把預設分頁大小(property;{@code plans} 表沒有這一格,它不是配額)與依方案查表的兩個上限
 * 收在一處:否則每個讀取端點都得同時注入 {@code CtipProperties} 與 {@code QuotaService},
 * 而 controller 的建構子已經在 checkstyle 的參數上限邊緣。
 */
@Component
public class ReadQuotaPolicy {

    private final QuotaService quotas;
    private final int defaultPageSize;

    ReadQuotaPolicy(QuotaService quotas, CtipProperties properties) {
        this.quotas = quotas;
        this.defaultPageSize = properties.api().defaultPageSize();
    }

    /** §9.3:超過上限夾到上限,不報錯;未給用預設(預設本身也不得超過方案上限)。 */
    public int clampPageSize(TenantId tenantId, Integer requested) {
        return quotas.clampPageSize(tenantId, requested, defaultPageSize);
    }

    /** §9.7:批次驗證的單次上限——超過是「這一次請求太大」,回 413。 */
    public void requireBatchLookupWithin(TenantId tenantId, int size) {
        quotas.requireBatchLookupWithin(tenantId, size);
    }
}
