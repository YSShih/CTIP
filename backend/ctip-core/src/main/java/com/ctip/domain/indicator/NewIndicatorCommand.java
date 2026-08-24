package com.ctip.domain.indicator;

import com.ctip.domain.source.Reputation;
import com.ctip.domain.tenant.TenantId;
import java.util.Objects;

/** 建立 Indicator 的參數物件(id 由呼叫端經 IdGeneratorPort 產生;不變量 I13:必須有第一筆來源記錄)。 */
public record NewIndicatorCommand(
        IndicatorId id,
        TenantId ownerTenantId,
        IocValue value,
        IndicatorSourceSnapshot firstReport,
        Reputation sourceReputation) {

    public NewIndicatorCommand {
        Objects.requireNonNull(id, "id 不得為 null");
        Objects.requireNonNull(ownerTenantId, "ownerTenantId 不得為 null");
        Objects.requireNonNull(value, "value 不得為 null");
        Objects.requireNonNull(firstReport, "firstReport 不得為 null(不變量 I13)");
        Objects.requireNonNull(sourceReputation, "sourceReputation 不得為 null");
    }
}
