package com.ctip.domain.shared;

import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.Tlp;
import java.util.Objects;

/**
 * 查詢可見度範圍(docs/spec/07-domain-intel.md §7.7 可見度表的 domain 表述):
 * viewer 可見「public tenant 中 tlp &lt;= maxPublicTlp 的資料」加上「自家 tenant 的全部資料」。
 * 匿名者的 viewerTenantId 即 public tenant、maxPublicTlp = CLEAR。
 */
public record Visibility(TenantId viewerTenantId, Tlp maxPublicTlp) {

    public Visibility {
        Objects.requireNonNull(viewerTenantId, "viewerTenantId 不得為 null");
        Objects.requireNonNull(maxPublicTlp, "maxPublicTlp 不得為 null");
    }

    public static Visibility anonymous() {
        return new Visibility(TenantId.PUBLIC, Tlp.CLEAR);
    }

    public static Visibility authenticated(TenantId ownTenantId) {
        return new Visibility(ownTenantId, Tlp.GREEN);
    }
}
