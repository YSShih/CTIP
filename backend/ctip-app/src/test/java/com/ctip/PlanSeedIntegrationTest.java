package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.port.PlanRepository;
import com.ctip.domain.plan.Plan;
import com.ctip.domain.plan.PlanCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * `V29__seed_plans_and_permissions.sql` 的種子值必須逐格等於
 * docs/spec/10-identity-plans.md §10.6 的配額表——那張表是所有配額的唯一真相來源。
 *
 * <p>與 {@code QuotaEnforcementTest} 分開:那支測的是「配額有沒有被強制」,
 * 這支測的是「配額值對不對」。種子改了而規格沒改,這支會先紅。
 */
class PlanSeedIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private PlanRepository plans;

    /** 逐格對照 §10.6。 */
    @Test
    void seededPlansMatchTheSpecificationTable() {
        Plan anonymous = plans.findByCode(PlanCode.ANONYMOUS).orElseThrow();
        Plan free = plans.findByCode(PlanCode.FREE).orElseThrow();
        Plan premium = plans.findByCode(PlanCode.PREMIUM).orElseThrow();
        Plan enterprise = plans.findByCode(PlanCode.ENTERPRISE).orElseThrow();

        assertThat(anonymous.requestsPerMinute().orElse(0)).isEqualTo(60);
        assertThat(anonymous.maxPageSize()).isEqualTo(50);
        assertThat(anonymous.maxApiKeys().isDisabled()).isTrue();
        assertThat(anonymous.stixExportMaxObjects().isDisabled()).isTrue();
        assertThat(free.maxManualSubmissionsPerDay().isDisabled()).isTrue();
        assertThat(free.maxImportRowsPerFile().isDisabled()).isTrue();
        assertThat(free.maxApiKeys().orElse(0)).isEqualTo(1);
        assertThat(premium.maxManualSubmissionsPerDay().orElse(0)).isEqualTo(1000);
        assertThat(premium.maxImportRowsPerFile().orElse(0)).isEqualTo(10_000);
        assertThat(premium.tenantBloomCapacity().orElse(0)).isEqualTo(1_000_000);
        // ENTERPRISE 的兩個 null 是「依合約 / 無限制」,不是 0
        assertThat(enterprise.requestsPerDay().isUnlimited()).isTrue();
        assertThat(enterprise.stixExportMaxObjects().isUnlimited()).isTrue();
    }
}
