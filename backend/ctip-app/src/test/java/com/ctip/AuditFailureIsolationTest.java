package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctip.application.audit.AuditEvent;
import com.ctip.application.audit.AuditLogQuery;
import com.ctip.application.audit.AuditRecord;
import com.ctip.application.port.AuditLogPort;
import com.ctip.application.port.AuditPort;
import com.ctip.domain.audit.AuditAction;
import com.ctip.domain.audit.AuditResult;
import com.ctip.domain.shared.CursorPage;
import com.ctip.domain.tenant.TenantId;
import com.ctip.infrastructure.audit.AuditWriter;
import com.ctip.support.LogCapture;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * M3-10:稽核寫入失敗<strong>不得</strong>使業務操作失敗
 * (docs/spec/13-platform-ops.md §13.5 規則 3)。
 *
 * <p>以一個永遠丟例外的 {@code AuditLogPort} 取代真正的持久化,然後:
 * <ul>
 *   <li>照樣打一個一般請求 → 仍然 200;</li>
 *   <li>直接呼叫 {@code AuditPort.record} → 不丟例外(呼叫端不必 try/catch);</li>
 *   <li>失敗有被記成 ERROR ——「不影響業務」不等於「安靜吞掉」。</li>
 * </ul>
 */
@AutoConfigureMockMvc
@Import(AuditFailureIsolationTest.FailingAuditStorage.class)
class AuditFailureIsolationTest extends AbstractPostgresIntegrationTest {

    private static final String CLIENT_IP = "10.80.0.11";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AuditPort audit;

    @Autowired
    private AuditWriter writer;

    @Test
    void aBusinessRequestStillSucceedsWhenTheAuditWriteFails() throws Exception {
        try (LogCapture logs = LogCapture.start()) {
            mvc.perform(get("/api/v1/iocs?limit=1").with(fromClient())).andExpect(status().isOk());
            writer.awaitQuiescence(5_000);
            assertThat(logs.text()).contains("稽核寫入失敗");
        }
    }

    @Test
    void recordNeverThrowsBackIntoTheCallingThread() {
        try (LogCapture logs = LogCapture.start()) {
            assertThatCode(() -> audit.record(
                            AuditEvent.system(AuditAction.ADMIN_ACTION, AuditResult.SUCCESS, TenantId.PUBLIC)))
                    .doesNotThrowAnyException();
            writer.awaitQuiescence(5_000);
            assertThat(logs.text()).contains("稽核寫入失敗");
        }
    }

    private static RequestPostProcessor fromClient() {
        return request -> {
            request.setRemoteAddr(CLIENT_IP);
            return request;
        };
    }

    /** 只換掉持久化面;{@code AuditWriter} 與 filter 都是正牌的。 */
    @TestConfiguration
    static class FailingAuditStorage {

        @Bean
        @Primary
        AuditLogPort failingAuditLogPort() {
            return new AuditLogPort() {
                @Override
                public void append(List<AuditRecord> records) {
                    throw new IllegalStateException("模擬:稽核資料表無法寫入");
                }

                @Override
                public CursorPage<AuditRecord> list(AuditLogQuery query) {
                    return CursorPage.lastPage(List.of());
                }

                @Override
                public com.ctip.application.audit.AuditActorSummary summarizeActor(UUID actorId) {
                    return com.ctip.application.audit.AuditActorSummary.empty();
                }
            };
        }
    }
}
