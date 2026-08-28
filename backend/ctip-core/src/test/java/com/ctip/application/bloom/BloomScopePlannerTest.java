package com.ctip.application.bloom;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.domain.bloom.BloomScope;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.tenant.TenantId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 誰有 tenant bloom、用多大的容量(docs/spec/11-sync-bloom.md §11.2)。
 * 配額一律讀 plans 表,不得在任何地方寫死。
 */
@Tag("unit")
class BloomScopePlannerTest {

    private final BloomTestHarness harness = new BloomTestHarness();

    @Test
    void thePublicTargetAlwaysExistsAndBelongsToThePublicTenant() {
        BloomTarget target = harness.planner.publicTarget();

        assertThat(target.scope()).isEqualTo(BloomScope.PUBLIC);
        assertThat(target.tenantId()).isEqualTo(TenantId.PUBLIC);
        assertThat(target.parameters().capacity()).isEqualTo(1_000L);
        assertThat(target.parameters().falsePositiveRate()).isEqualTo(0.01);
    }

    @Test
    void aPlanWithoutTenantBloomCapacityGetsNoTenantBloom() {
        harness.subscribe(BloomTestHarness.TENANT, PlanCode.FREE);

        // FREE 的 tenant_bloom_capacity 是 NULL,§11.2 的語意是「無 tenant Bloom」
        assertThat(harness.planner.tenantTarget(BloomTestHarness.TENANT)).isEmpty();
        assertThat(harness.planner.targets()).hasSize(1);
    }

    @Test
    void aTenantBloomIsSizedToItsMembersButNeverAboveThePlanEntitlement() {
        harness.subscribe(BloomTestHarness.TENANT, PlanCode.PREMIUM);

        BloomTarget small =
                harness.planner.tenantTarget(BloomTestHarness.TENANT).orElseThrow();
        assertThat(small.parameters().capacity())
                .as("成員少於預設尺寸時用預設尺寸,不為此配置整份方案上限")
                .isEqualTo(100L);

        for (int i = 0; i < 150; i++) {
            harness.members.add(BloomScope.TENANT, BloomTestHarness.TENANT, "member-" + i, BloomTestHarness.NOW);
        }
        BloomTarget grown =
                harness.planner.tenantTarget(BloomTestHarness.TENANT).orElseThrow();

        assertThat(grown.parameters().capacity()).isEqualTo(150L);
        assertThat(grown.scope()).isEqualTo(BloomScope.TENANT);
        assertThat(harness.planner.targets()).hasSize(2);
    }
}
