package com.ctip.application.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.domain.audit.AuditAction;
import com.ctip.domain.audit.AuditActorType;
import com.ctip.domain.audit.AuditResult;
import com.ctip.domain.tenant.TenantId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** 稽核事件的建構與 metadata 清洗(docs/spec/13-platform-ops.md §13.5 規則 5)。 */
@Tag("unit")
class AuditEventTest {

    @Test
    void metadataKeysThatCouldCarryCredentialsAreRedacted() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("path", "/api/v1/iocs");
        raw.put("Authorization", "Bearer abc.def.ghi");
        raw.put("apiKey", "ctip_mvp_secret");
        raw.put("user_password", "hunter2");
        raw.put("refreshToken", "tok");

        Map<String, Object> clean = AuditEvent.of(AuditAction.API_ACCESS, AuditResult.SUCCESS)
                .withMetadata(raw)
                .metadata();

        assertThat(clean).containsEntry("path", "/api/v1/iocs");
        assertThat(clean)
                .containsEntry("Authorization", AuditMetadata.REDACTED)
                .containsEntry("apiKey", AuditMetadata.REDACTED)
                .containsEntry("user_password", AuditMetadata.REDACTED)
                .containsEntry("refreshToken", AuditMetadata.REDACTED);
    }

    /** 過長的值會截斷:metadata 是 JSONB,單一欄位不該把一整個請求本文帶進來。 */
    @Test
    void longValuesAreTruncated() {
        String huge = "x".repeat(2000);

        Object stored = AuditEvent.of(AuditAction.API_ACCESS, AuditResult.SUCCESS)
                .withMetadata(Map.of("body", huge))
                .metadata()
                .get("body");

        assertThat((String) stored).hasSize(512);
    }

    @Test
    void explicitActorAndTenantOverrideTheDefaults() {
        UUID actorId = UUID.randomUUID();
        AuditEvent event = AuditEvent.system(AuditAction.TENANT_CREATED, AuditResult.SUCCESS, TenantId.PUBLIC)
                .withActor(AuditActorType.USER, actorId)
                .withResource("tenant", actorId);

        assertThat(event.actorType()).isEqualTo(AuditActorType.USER);
        assertThat(event.actorId()).isEqualTo(actorId);
        assertThat(event.tenantId()).isEqualTo(TenantId.PUBLIC);
        assertThat(event.resourceType()).isEqualTo("tenant");
    }

    @Test
    void anEventWithoutActionOrResultIsRejected() {
        assertThatThrownBy(() -> AuditEvent.of(null, AuditResult.SUCCESS)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuditEvent.of(AuditAction.LOGIN, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullMetadataBecomesAnEmptyMap() {
        assertThat(AuditEvent.of(AuditAction.LOGIN, AuditResult.SUCCESS).metadata())
                .isEmpty();
    }
}
