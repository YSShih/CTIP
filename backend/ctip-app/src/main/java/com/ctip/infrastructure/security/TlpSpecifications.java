package com.ctip.infrastructure.security;

import com.ctip.domain.shared.Visibility;
import com.ctip.domain.tenant.TenantId;
import com.ctip.infrastructure.persistence.IndicatorEntity;
import com.ctip.infrastructure.persistence.IndicatorSourceEntity;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.Tlp;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * 唯一一套 tenant + TLP + 再散布過濾邏輯(docs/spec/01-architecture.md §1.11、07 §7.7/§7.9)。
 * 所有 tenant-scoped 查詢一律附加本 Specification,不得在 controller 手動傳 tenantId。
 *
 * <p>可見 = 「自家 tenant 的全部」∪「public tenant 中 tlp &lt;= maxPublicTlp 者」;
 * 非擁有租戶的可見性另須至少一個非 INTERNAL_ONLY 的來源(不變量 I14);
 * 軟刪除(deleted_at 非 null)一律不可見。
 */
public final class TlpSpecifications {

    private TlpSpecifications() {}

    public static Specification<IndicatorEntity> visibleTo(Visibility visibility) {
        return (root, query, cb) -> {
            Predicate scope = tenantScope(root, cb, visibility);
            Predicate redistribution = ownerOrRedistributable(root, query, cb, visibility.viewerTenantId());
            return cb.and(scope, redistribution, cb.isNull(root.get("deletedAt")));
        };
    }

    /** owner IN (viewer, public) 的展開:public 分支帶 TLP 上限;匿名(viewer=public)只剩 public 分支。 */
    private static Predicate tenantScope(Root<IndicatorEntity> root, CriteriaBuilder cb, Visibility visibility) {
        Predicate publicBranch = cb.and(
                cb.equal(root.get("ownerTenantId"), TenantId.PUBLIC.value()),
                root.get("tlp").in(visibleTlpNames(visibility.maxPublicTlp())));
        if (visibility.viewerTenantId().isPublic()) {
            return publicBranch;
        }
        return cb.or(
                cb.equal(root.get("ownerTenantId"), visibility.viewerTenantId().value()), publicBranch);
    }

    /** I14 / 07 §7.9 規則 3:viewer == owner 免過濾;否則須存在非 INTERNAL_ONLY 的來源記錄。 */
    private static Predicate ownerOrRedistributable(
            Root<IndicatorEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb, TenantId viewer) {
        Predicate viewerIsOwner = cb.equal(root.get("ownerTenantId"), viewer.value());
        Subquery<UUID> redistributable = query.subquery(UUID.class);
        Root<IndicatorSourceEntity> record = redistributable.from(IndicatorSourceEntity.class);
        redistributable
                .select(record.get("id"))
                .where(
                        cb.equal(record.get("indicator"), root),
                        cb.notEqual(record.get("redistributionPolicy"), RedistributionPolicy.INTERNAL_ONLY.name()));
        return cb.or(viewerIsOwner, cb.exists(redistributable));
    }

    private static List<String> visibleTlpNames(Tlp maxPublicTlp) {
        return Arrays.stream(Tlp.values())
                .filter(tlp -> tlp.isNoStricterThan(maxPublicTlp))
                .map(Enum::name)
                .toList();
    }
}
