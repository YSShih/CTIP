package com.ctip.application.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.port.AuditLogPort;
import com.ctip.domain.audit.AuditAction;
import com.ctip.domain.shared.CursorPage;
import com.ctip.domain.tenant.TenantId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** 稽核查詢:查詢條件原樣交給 port——租戶範圍由呼叫端的身分決定,服務不得再改寫。 */
@Tag("unit")
class AuditQueryServiceTest {

    @Test
    void theQueryReachesThePortUnchanged() {
        RecordingAuditLogs port = new RecordingAuditLogs();
        AuditLogQuery query = new AuditLogQuery(TenantId.PUBLIC, AuditAction.LOGIN, null, 25);

        new AuditQueryService(port).list(query);

        assertThat(port.queries).containsExactly(query);
    }

    private static final class RecordingAuditLogs implements AuditLogPort {

        private final List<AuditLogQuery> queries = new ArrayList<>();

        @Override
        public void append(List<AuditRecord> records) {
            throw new UnsupportedOperationException("查詢路徑不寫入");
        }

        @Override
        public CursorPage<AuditRecord> list(AuditLogQuery query) {
            queries.add(query);
            return CursorPage.lastPage(List.of());
        }

        @Override
        public AuditActorSummary summarizeActor(UUID actorId) {
            return AuditActorSummary.empty();
        }
    }
}
