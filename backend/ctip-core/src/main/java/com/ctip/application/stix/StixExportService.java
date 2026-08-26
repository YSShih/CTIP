package com.ctip.application.stix;

import com.ctip.application.indicator.IndicatorFilter;
import com.ctip.application.indicator.RedistributionFilter;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.IndicatorRepository;
import com.ctip.application.port.StixObjectPort;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.shared.Cursor;
import com.ctip.domain.shared.CursorPage;
import com.ctip.domain.shared.Visibility;
import com.ctip.domain.stix.StixIndicatorProjector;
import com.ctip.domain.stix.StixTlpMarkings;
import com.ctip.sdk.Tlp;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * bundle 匯出(docs/spec/07-domain-intel.md §7.8.5):
 * 經統一可見度(tenant + TLP)與再散布過濾;只含實際被引用的 marking-definition;
 * 物件數上限依方案——M1 尚無 plans 表,以 {@link StixExportSettings} 的 property 預設承載
 * (Phase 14 移入 plans.stix_export_max_objects);超過丟出例外由 API 層映射 403。
 * bundle id 每次匯出重新產生(bundle 非可持久化物件,無 created/modified)。
 */
@Service
public class StixExportService {

    private static final int PAGE_SIZE = 200;

    private final IndicatorRepository indicators;
    private final StixObjectPort stixObjects;
    private final RedistributionFilter redistribution;
    private final IdGeneratorPort idGenerator;
    private final StixExportSettings settings;

    public StixExportService(
            IndicatorRepository indicators,
            StixObjectPort stixObjects,
            RedistributionFilter redistribution,
            IdGeneratorPort idGenerator,
            StixExportSettings settings) {
        this.indicators = indicators;
        this.stixObjects = stixObjects;
        this.redistribution = redistribution;
        this.idGenerator = idGenerator;
        this.settings = settings;
    }

    public StixBundle exportBundle(Visibility visibility) {
        List<Indicator> exportable = collectExportable(visibility);
        Set<Tlp> usedTlps = EnumSet.noneOf(Tlp.class);
        List<String> stixIds = new ArrayList<>();
        for (Indicator indicator : exportable) {
            usedTlps.add(indicator.tlp());
            stixIds.add(StixIndicatorProjector.stixId(indicator.snapshot()));
        }
        List<Map<String, Object>> markings =
                usedTlps.stream().map(StixTlpMarkings::marking).toList();
        if (exportable.size() + markings.size() > settings.maxObjects()) {
            throw new StixExportLimitExceededException(settings.maxObjects());
        }
        Map<String, String> contents = stixObjects.findContents(stixIds);
        List<String> indicatorContents =
                stixIds.stream().map(contents::get).filter(Objects::nonNull).toList();
        return new StixBundle("bundle--" + idGenerator.nextId(), markings, indicatorContents);
    }

    /** 掃描上限 maxObjects + 1:足以判定超限,不對超大資料集全量掃描。 */
    private List<Indicator> collectExportable(Visibility visibility) {
        List<Indicator> exportable = new ArrayList<>();
        Cursor after = null;
        while (exportable.size() <= settings.maxObjects()) {
            CursorPage<Indicator> page = indicators.findVisible(visibility, IndicatorFilter.none(), after, PAGE_SIZE);
            page.items().stream()
                    .filter(i -> redistribution.redistributableTo(i, visibility.viewerTenantId()))
                    .forEach(exportable::add);
            if (!page.hasMore()) {
                break;
            }
            after = Cursor.decode(page.nextCursor());
        }
        return exportable;
    }
}
