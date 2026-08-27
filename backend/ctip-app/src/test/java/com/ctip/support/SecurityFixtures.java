package com.ctip.support;

import com.ctip.application.port.IndicatorRepository;
import com.ctip.application.port.SourceRepository;
import com.ctip.application.port.TenantRepository;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.tenant.Tenant;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.tenant.TenantSlug;
import com.ctip.domain.tenant.TenantType;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.SourceType;
import com.ctip.sdk.Tlp;
import java.util.List;
import java.util.UUID;

/**
 * 安全測試(docs/spec/14-testing.md §14.4)的固定樣本:涵蓋 TLP × 擁有租戶 × 再散布政策的
 * 判別性組合。{@link #PUBLIC_CLEAR_INTERNAL} 是唯一能判別
 * {@code TlpSpecifications.ownerOrRedistributable} 的樣本(ADR 0006 回歸鎖)。
 */
public final class SecurityFixtures {

    public static final TenantId DEMO = new TenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    public static final TenantId TENANT_B = new TenantId(UUID.fromString("00000000-0000-0000-0000-00000000000b"));

    public static final IndicatorId PUBLIC_GREEN = id("41");
    public static final IndicatorId PUBLIC_CLEAR = id("42");
    public static final IndicatorId B_AMBER = id("43");
    public static final IndicatorId DEMO_INTERNAL_ONLY = id("44");
    public static final IndicatorId PUBLIC_CLEAR_INTERNAL = id("45");

    private SecurityFixtures() {}

    /** 冪等:整合測試共用同一個資料庫容器,重複呼叫不重建。 */
    public static void seed(TenantRepository tenants, SourceRepository sources, IndicatorRepository indicators) {
        if (tenants.findById(TENANT_B).isEmpty()) {
            tenants.save(Tenant.create(TENANT_B, new TenantSlug("sec-test-b"), "Tenant B", TenantType.ORGANIZATION));
        }
        SourceId sourceId = sources.findBySourceType(SourceType.MOCK_OPENPHISH)
                .orElseThrow()
                .id();
        for (IndicatorFixtures.Fixture fixture : fixtures()) {
            IndicatorFixtures.upsert(indicators, sourceId, fixture);
        }
    }

    private static List<IndicatorFixtures.Fixture> fixtures() {
        return List.of(
                new IndicatorFixtures.Fixture(
                        PUBLIC_CLEAR,
                        TenantId.PUBLIC,
                        Tlp.CLEAR,
                        RedistributionPolicy.PUBLIC_REDISTRIBUTABLE,
                        "sec-clear"),
                new IndicatorFixtures.Fixture(
                        PUBLIC_GREEN,
                        TenantId.PUBLIC,
                        Tlp.GREEN,
                        RedistributionPolicy.PUBLIC_REDISTRIBUTABLE,
                        "sec-green"),
                new IndicatorFixtures.Fixture(
                        B_AMBER, TENANT_B, Tlp.AMBER, RedistributionPolicy.ATTRIBUTION_REQUIRED, "sec-b-amber"),
                new IndicatorFixtures.Fixture(
                        DEMO_INTERNAL_ONLY, DEMO, Tlp.AMBER, RedistributionPolicy.INTERNAL_ONLY, "sec-internal"),
                new IndicatorFixtures.Fixture(
                        PUBLIC_CLEAR_INTERNAL,
                        TenantId.PUBLIC,
                        Tlp.CLEAR,
                        RedistributionPolicy.INTERNAL_ONLY,
                        "sec-pub-int"));
    }

    private static IndicatorId id(String suffix) {
        return new IndicatorId(UUID.fromString("00000000-0000-0000-0000-0000000000" + suffix));
    }
}
