package com.ctip.infrastructure.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.domain.audit.AuditAction;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 端點 → 稽核行為的對照(docs/spec/13-platform-ops.md §13.5 觸發點對照表)。
 *
 * <p>對照表是強制規格,所以這裡逐列驗;漏一列的後果是那個行為永遠不會被記錄,
 * 而整合測試的 {@code AuditCompletenessTest} 只看得出「有沒有寫入過」,看不出寫錯了哪一種。
 */
@Tag("unit")
class AuditEndpointsTest {

    @ParameterizedTest
    @CsvSource({
        "POST,/api/v1/auth/login,LOGIN,user",
        "POST,/api/v1/auth/logout,LOGOUT,user",
        "POST,/api/v1/auth/refresh,TOKEN_REFRESH,refresh_token",
        "GET,/api/v1/iocs,IOC_QUERY,indicator",
        "POST,/api/v1/iocs/search,IOC_QUERY,indicator",
        "POST,/api/v1/iocs/lookup,IOC_QUERY,indicator",
        "POST,/api/v1/iocs,IOC_SUBMIT,indicator",
        "POST,/api/v1/iocs/import,IOC_IMPORT,import_job",
        "GET,/api/v1/stix/bundle,STIX_EXPORT,stix_bundle",
        "GET,/api/v1/sync/manifest,SYNC_MANIFEST,bloom_version",
        "GET,/api/v1/sync/bloom,SYNC_BLOOM,bloom_artifact",
        "GET,/api/v1/sync/delta,SYNC_DELTA,bloom_artifact",
        "POST,/api/v1/webhooks,WEBHOOK_CREATED,webhook",
        "GET,/api/v1/admin/tenants,ADMIN_ACTION,tenants"
    })
    void theTriggerTableIsMappedExactly(String method, String path, AuditAction action, String resourceType) {
        Optional<AuditEndpoints> matched = AuditEndpoints.match(method, path);

        assertThat(matched).isPresent();
        assertThat(matched.orElseThrow().action()).isEqualTo(action);
        assertThat(matched.orElseThrow().resourceType()).isEqualTo(resourceType);
    }

    /** §13.5 規則 4:只有讀取類的兩支取樣,其餘 100%。 */
    @Test
    void onlyTheOnePercentReadsAreSampled() {
        assertThat(AuditEndpoints.match("GET", "/api/v1/iocs").orElseThrow().sampled())
                .isTrue();
        assertThat(AuditEndpoints.match("GET", "/api/v1/sync/manifest")
                        .orElseThrow()
                        .sampled())
                .isTrue();
        assertThat(AuditEndpoints.match("GET", "/api/v1/sync/bloom")
                        .orElseThrow()
                        .sampled())
                .isFalse();
        assertThat(AuditEndpoints.match("POST", "/api/v1/iocs").orElseThrow().sampled())
                .isFalse();
    }

    @Test
    void resourceIdsAreExtractedFromThePathWhereTheTableNamesOne() {
        UUID id = UUID.fromString("3f4a1c0e-2b7d-4f10-9c11-8a2e5d6b7c90");

        assertThat(AuditEndpoints.match("GET", "/api/v1/iocs/" + id + "/sources")
                        .orElseThrow())
                .satisfies(matched -> {
                    assertThat(matched.action()).isEqualTo(AuditAction.IOC_DOWNLOAD);
                    assertThat(matched.resourceId()).isEqualTo(id);
                });
        assertThat(AuditEndpoints.match("POST", "/api/v1/iocs/" + id + "/report-false-positive")
                        .orElseThrow()
                        .action())
                .isEqualTo(AuditAction.IOC_REPORT_FP);
        assertThat(AuditEndpoints.match("DELETE", "/api/v1/webhooks/" + id).orElseThrow())
                .satisfies(matched -> {
                    assertThat(matched.action()).isEqualTo(AuditAction.WEBHOOK_DELETED);
                    assertThat(matched.resourceId()).isEqualTo(id);
                });
    }

    /** 對照表沒有列的端點不產生額外行為代碼——它們只會有 API_ACCESS。 */
    @Test
    void endpointsOutsideTheTableDoNotMatch() {
        assertThat(AuditEndpoints.match("GET", "/api/v1/threats")).isEmpty();
        assertThat(AuditEndpoints.match("GET", "/api/v1/stats/summary")).isEmpty();
        assertThat(AuditEndpoints.match("GET", "/actuator/health")).isEmpty();
        assertThat(AuditEndpoints.match("PUT", "/api/v1/iocs")).isEmpty();
    }
}
