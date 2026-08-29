package com.ctip.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 詞彙比對(docs/spec/13-platform-ops.md §13.7「精確查詢、前綴查詢、模糊查詢(僅 M2)」)。
 *
 * <p>{@code normalizedValue} 是 keyword 欄位,子字串以 {@code wildcard} 表達——它同時涵蓋精確與
 * 前綴,語意與 M1 的 {@code LIKE '%term%'} 完全一致,升級後端不會讓同一個查詢回不同的結果集。
 *
 * <p>{@code fuzzy=true} 時<strong>加上</strong>(而非取代)Levenshtein 比對,這是 typosquatting
 * 偵測要的東西:{@code paypa1.com} 查得到 {@code paypal.com}。{@code prefixLength=1} 讓首字元必須
 * 相符,否則短字串會把整個索引都當成候選。
 */
final class SearchTermQuery {

    private static final String FUZZINESS = "AUTO";
    private static final int PREFIX_LENGTH = 1;
    private static final int MAX_EXPANSIONS = 50;

    private SearchTermQuery() {}

    static Query of(String term, boolean fuzzy) {
        String needle = term.toLowerCase(Locale.ROOT);
        List<Query> should = new ArrayList<>();
        should.add(QueryBuilders.wildcard(
                w -> w.field(SearchFields.NORMALIZED_VALUE).value("*" + escapeWildcard(needle) + "*")));
        if (fuzzy) {
            should.add(QueryBuilders.fuzzy(f -> f.field(SearchFields.NORMALIZED_VALUE)
                    .value(needle)
                    .fuzziness(FUZZINESS)
                    .prefixLength(PREFIX_LENGTH)
                    .maxExpansions(MAX_EXPANSIONS)));
        }
        return QueryBuilders.bool(b -> b.should(should).minimumShouldMatch("1"));
    }

    /** 使用者輸入的 * ? \ 視為字面值(對應 PostgreSQL 路徑對 % _ \ 的跳脫)。 */
    static String escapeWildcard(String term) {
        return term.replace("\\", "\\\\").replace("*", "\\*").replace("?", "\\?");
    }
}
