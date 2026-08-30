package com.ctip.domain.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 稽核列舉的契約(docs/spec/04-data-dictionary.md §4.5、13 §13.5:<strong>26 種</strong>行為)。
 *
 * <p>這裡只驗「列舉本身與規格一致」;「每一種都有實際寫入路徑」由整合測試
 * {@code AuditCompletenessTest} 驗——那是兩件不同的事,少了後者,這裡全綠也可能有
 * 永不可達的行為(執行規則 16)。
 */
@Tag("unit")
class AuditActionTest {

    /** §4.5「稽核行為」區塊逐字。 */
    private static final List<String> SPECIFIED = List.of(
            "LOGIN",
            "LOGIN_FAILED",
            "LOGOUT",
            "TOKEN_REFRESH",
            "TOKEN_REUSE_DETECTED",
            "API_ACCESS",
            "IOC_QUERY",
            "IOC_DOWNLOAD",
            "IOC_SUBMIT",
            "IOC_IMPORT",
            "IOC_REPORT_FP",
            "STIX_EXPORT",
            "SYNC_MANIFEST",
            "SYNC_BLOOM",
            "SYNC_DELTA",
            "INGESTION_STARTED",
            "INGESTION_COMPLETED",
            "INGESTION_FAILED",
            "ADMIN_ACTION",
            "TENANT_CREATED",
            "USER_CREATED",
            "API_KEY_CREATED",
            "API_KEY_REVOKED",
            "SUBSCRIPTION_CHANGED",
            "WEBHOOK_CREATED",
            "WEBHOOK_DELETED");

    @Test
    void theEnumMatchesTheSpecifiedTwentySixActions() {
        assertThat(Arrays.stream(AuditAction.values()).map(Enum::name))
                .containsExactlyInAnyOrderElementsOf(SPECIFIED)
                .hasSize(26);
    }

    @Test
    void actorTypesAndResultsMatchTheColumnConstraints() {
        assertThat(Arrays.stream(AuditActorType.values()).map(Enum::name))
                .containsExactlyInAnyOrder("ANONYMOUS", "USER", "API_KEY", "SYSTEM");
        assertThat(Arrays.stream(AuditResult.values()).map(Enum::name))
                .containsExactlyInAnyOrder("SUCCESS", "FAILURE", "DENIED");
    }
}
