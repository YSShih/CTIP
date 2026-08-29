package com.ctip.application.indicator;

import com.ctip.application.port.IndicatorRepository;
import com.ctip.application.port.SearchPort;
import com.ctip.application.port.SearchQuery;
import com.ctip.application.port.SearchResult;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.indicator.normalization.IocFormatException;
import com.ctip.domain.indicator.normalization.IocNormalizers;
import com.ctip.domain.shared.Cursor;
import com.ctip.domain.shared.CursorPage;
import com.ctip.domain.shared.Visibility;
import com.ctip.sdk.IocType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * IOC 讀取端點的查詢編排(docs/spec/09-api.md §9.1、§9.5)。
 * 可見度(tenant + TLP + 再散布規則 3)由 repository/Specification 統一強制(§9.5 第 1–3 步),
 * 本服務不重複過濾;規則 4/5 的輸出遮罩由 {@link RedistributionFilter} 於 DTO 映射時判定。
 */
@Service
public class IndicatorQueryService {

    private final IndicatorRepository indicators;
    private final SearchPort search;
    private final IocNormalizers normalizers;

    public IndicatorQueryService(IndicatorRepository indicators, SearchPort search, IocNormalizers normalizers) {
        this.indicators = indicators;
        this.search = search;
        this.normalizers = normalizers;
    }

    public CursorPage<Indicator> list(IndicatorFilter filter, Visibility visibility, Cursor after, int limit) {
        return indicators.findVisible(visibility, filter, after, limit);
    }

    public List<Indicator> listOffset(IndicatorFilter filter, Visibility visibility, int offset, int limit) {
        return indicators.findVisibleOffset(visibility, filter, offset, limit);
    }

    public Optional<Indicator> byId(IndicatorId id, Visibility visibility) {
        return indicators.findVisibleById(id, visibility);
    }

    /**
     * 搜尋(§13.7)。回傳型別帶「哪個後端服務了這次查詢」,供 {@code X-Search-Backend} 使用——
     * 降級判斷在 {@code FallbackSearchAdapter},controller 只負責把答案寫進標頭。
     */
    public SearchResult search(SearchQuery query) {
        return search.search(query);
    }

    /**
     * 批次精確驗證(§9.1 lookup、11 §11.6):逐值清理 → 推斷型別 → 正規化 → 識別鍵查詢。
     * 無法正規化或不可見一律回未命中,不報錯、不洩漏存在性。
     */
    public List<LookupResult> lookup(List<String> values, Visibility visibility) {
        List<LookupResult> results = new ArrayList<>(values.size());
        for (String value : values) {
            results.add(lookupOne(value, visibility));
        }
        return results;
    }

    private LookupResult lookupOne(String value, Visibility visibility) {
        try {
            String cleaned = normalizers.clean(value);
            IocType type = normalizers.infer(cleaned);
            if (type == null) {
                return LookupResult.miss(value);
            }
            String normalized = normalizers.normalize(type, cleaned);
            return indicators
                    .findVisibleByIdentity(type, normalized, visibility)
                    .map(indicator -> LookupResult.hit(value, indicator))
                    .orElseGet(() -> LookupResult.miss(value));
        } catch (IocFormatException | IllegalArgumentException e) {
            return LookupResult.miss(value);
        }
    }
}
