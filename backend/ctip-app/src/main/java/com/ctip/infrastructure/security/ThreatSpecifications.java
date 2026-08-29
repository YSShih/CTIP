package com.ctip.infrastructure.security;

import com.ctip.domain.shared.Visibility;
import com.ctip.domain.tenant.TenantId;
import com.ctip.infrastructure.persistence.ThreatEntity;
import com.ctip.sdk.Tlp;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.Arrays;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * threats 的可見度述詞(docs/spec/07-domain-intel.md §7.7;ADR 0027)。
 *
 * <p>phase-18 明列「三個 /threats 端點的可見度述詞未定義」。定調為 §7.7 的通則:
 * 「自家 tenant 的全部」∪「public tenant 中 {@code tlp <= maxPublicTlp} 者」——與
 * {@link TlpSpecifications} 的租戶／TLP 段完全相同,兩者不得各寫一套。
 *
 * <p>與 Indicator 的兩點差異,都是資料模型的事實而非放寬:
 * <ul>
 *   <li><strong>沒有再散布維度</strong>:§7.9 規則 3 的條件是「所有<em>來源記錄</em>皆
 *       INTERNAL_ONLY」,而 threats 沒有 {@code indicator_sources} 這種來源記錄——
 *       Threat 是平台自己策展的分類,不是來源提供的原始資料。關聯的 IOC 仍各自受
 *       {@link TlpSpecifications} 過濾(見 {@code ThreatQueryService.linkedIndicators}),
 *       所以「經由威脅列舉他人私有情資」這條路被堵在關聯查詢那一層。</li>
 *   <li><strong>沒有軟刪除</strong>:表 19 沒有 {@code deleted_at};退役以
 *       {@code status = RETIRED} 表達,由 {@code ThreatFilterSpecs} 預設排除。</li>
 * </ul>
 */
public final class ThreatSpecifications {

    private ThreatSpecifications() {}

    public static Specification<ThreatEntity> visibleTo(Visibility visibility) {
        return (root, query, cb) -> tenantScope(root, cb, visibility);
    }

    /** owner IN (viewer, public) 的展開:public 分支帶 TLP 上限;匿名(viewer=public)只剩 public 分支。 */
    private static Predicate tenantScope(Root<ThreatEntity> root, CriteriaBuilder cb, Visibility visibility) {
        Predicate publicBranch = cb.and(
                cb.equal(root.get("ownerTenantId"), TenantId.PUBLIC.value()),
                root.get("tlp").in(visibleTlpNames(visibility.maxPublicTlp())));
        if (visibility.viewerTenantId().isPublic()) {
            return publicBranch;
        }
        return cb.or(
                cb.equal(root.get("ownerTenantId"), visibility.viewerTenantId().value()), publicBranch);
    }

    private static List<String> visibleTlpNames(Tlp maxPublicTlp) {
        return Arrays.stream(Tlp.values())
                .filter(tlp -> tlp.isNoStricterThan(maxPublicTlp))
                .map(Enum::name)
                .toList();
    }
}
