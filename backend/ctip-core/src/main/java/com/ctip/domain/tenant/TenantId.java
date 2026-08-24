package com.ctip.domain.tenant;

import java.util.Objects;
import java.util.UUID;

/** Tenant 識別碼。public tenant 為固定常數(docs/spec/10-identity-plans.md §10.1)。 */
public record TenantId(UUID value) {

    /** 公開系統租戶,承載公開情資;匿名請求一律綁定至此。 */
    public static final TenantId PUBLIC = new TenantId(new UUID(0L, 0L));

    public TenantId {
        Objects.requireNonNull(value, "value 不得為 null");
    }

    public boolean isPublic() {
        return this.equals(PUBLIC);
    }
}
