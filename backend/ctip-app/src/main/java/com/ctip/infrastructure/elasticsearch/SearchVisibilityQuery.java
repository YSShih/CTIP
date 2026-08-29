package com.ctip.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import com.ctip.domain.shared.Visibility;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.Tlp;
import java.util.Arrays;
import java.util.List;

/**
 * 可見度述詞在 Elasticsearch 上的重建(docs/spec/07-domain-intel.md §7.7、§7.9 規則 3;
 * 01 §1.11「可見度是查詢輸入,不得事後過濾」)。
 *
 * <p>⚠️ 這是 {@code TlpSpecifications} 的第二個實作。兩者必須表達完全相同的規則:
 *
 * <ul>
 *   <li>租戶範圍:自家全部 ∪ public 租戶中 tlp ≤ maxPublicTlp 者;匿名(viewer = public)只剩後者
 *   <li>再散布(I14 / §7.9 規則 3):viewer 是擁有租戶(且非 public)豁免,否則須有非
 *       {@code INTERNAL_ONLY} 的來源記錄
 * </ul>
 *
 * <p>軟刪除不需要述詞:軟刪除的 indicator 從不進索引({@code SearchDocumentPort} 一律排除),
 * 而且結果最後仍會經 {@code IndicatorRepository.findVisibleByIds} 由 PostgreSQL 再過濾一次。
 */
final class SearchVisibilityQuery {

    private SearchVisibilityQuery() {}

    static List<Query> of(Visibility visibility) {
        return List.of(tenantScope(visibility), redistribution(visibility.viewerTenantId()));
    }

    private static Query tenantScope(Visibility visibility) {
        Query publicBranch = QueryBuilders.bool(b ->
                b.filter(owner(TenantId.PUBLIC), terms(SearchFields.TLP, visibleTlpNames(visibility.maxPublicTlp()))));
        if (visibility.viewerTenantId().isPublic()) {
            return publicBranch;
        }
        return QueryBuilders.bool(
                b -> b.should(owner(visibility.viewerTenantId()), publicBranch).minimumShouldMatch("1"));
    }

    /** public 租戶無成員;匿名雖綁 public 仍屬公開輸出,不得豁免(與 domain I14、TlpSpecifications 同一規則)。 */
    private static Query redistribution(TenantId viewer) {
        Query redistributable =
                QueryBuilders.term(t -> t.field(SearchFields.REDISTRIBUTABLE).value(true));
        if (viewer.isPublic()) {
            return redistributable;
        }
        return QueryBuilders.bool(b -> b.should(owner(viewer), redistributable).minimumShouldMatch("1"));
    }

    static Query owner(TenantId tenant) {
        return QueryBuilders.term(
                t -> t.field(SearchFields.OWNER_TENANT_ID).value(tenant.value().toString()));
    }

    static Query terms(String field, List<String> values) {
        List<FieldValue> fieldValues = values.stream().map(FieldValue::of).toList();
        return QueryBuilders.terms(t -> t.field(field).terms(v -> v.value(fieldValues)));
    }

    private static List<String> visibleTlpNames(Tlp maxPublicTlp) {
        return Arrays.stream(Tlp.values())
                .filter(tlp -> tlp.isNoStricterThan(maxPublicTlp))
                .map(Enum::name)
                .toList();
    }
}
