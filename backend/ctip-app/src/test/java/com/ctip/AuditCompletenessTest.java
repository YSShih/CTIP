package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctip.application.identity.AuthService;
import com.ctip.application.identity.AuthSession;
import com.ctip.application.ingestion.BatchOutcome;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.PlanRepository;
import com.ctip.application.port.SourceRepository;
import com.ctip.application.port.SubscriptionRepository;
import com.ctip.application.port.TenantMembershipRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.application.source.SourceSyncRecorder;
import com.ctip.domain.audit.AuditAction;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.source.Source;
import com.ctip.infrastructure.audit.AuditWriter;
import com.ctip.sdk.SourceType;
import com.ctip.support.AuditProbe;
import com.ctip.support.TestIdentities;
import com.ctip.support.TestPlans;
import java.util.EnumSet;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

/**
 * M3-11b:26 種稽核行為<strong>皆有實際寫入路徑</strong>——沒有永不可達的行為
 * (docs/spec/13-platform-ops.md §13.5 觸發點對照表;00-master.md 執行規則 16)。
 *
 * <p>做法刻意是「真的把每一條路徑走一遍,再問資料庫留下了哪些 action」,
 * 而不是比對程式碼裡出現過哪些列舉值:後者對「有寫程式碼但永遠不會被呼叫」完全無感,
 * 而那正是這條判準要防的事。
 */
@AutoConfigureMockMvc
class AuditCompletenessTest extends AbstractPostgresIntegrationTest {

    private static final String CLIENT_IP = "10.90.0.11";
    private static final String PASSWORD = TestIdentities.PASSWORD;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private AuthService authService;

    @Autowired
    private TenantMembershipRepository memberships;

    @Autowired
    private PlanRepository plans;

    @Autowired
    private SubscriptionRepository subscriptions;

    @Autowired
    private IdGeneratorPort idGenerator;

    @Autowired
    private ClockPort clock;

    @Autowired
    private SourceRepository sources;

    @Autowired
    private SourceSyncRecorder recorder;

    @Autowired
    private AuditWriter writer;

    @Autowired
    private DataSource dataSource;

    private TestIdentities identities;
    private AuditProbe probe;

    @BeforeEach
    void setUp() {
        identities = new TestIdentities(authService, memberships);
        probe = new AuditProbe(writer, dataSource);
    }

    /**
     * 還原被 {@link #driveIngestionActions()} 動到的來源狀態。
     *
     * <p>{@code recorder.completed} 會寫 {@code last_sync_at},而 {@code Source.isDueForSync}
     * 看的就是它——不還原的話,{@code IngestionEndToEndTest} 的「三個來源都同步」
     * 會少掉這一個,而失敗訊息(「expected size 3 but was 2」)完全看不出是誰造成的。
     */
    @AfterEach
    void restoreSourceState() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM source_sync WHERE source_id IN"
                + " (SELECT id FROM sources WHERE source_type = 'MOCK_OPENPHISH')");
        jdbc.update("UPDATE sources SET status = 'ACTIVE', consecutive_failures = 0, last_sync_at = NULL,"
                + " last_success_at = NULL, last_failure_at = NULL, last_error_message = NULL,"
                + " avg_latency_ms = NULL, next_cursor = NULL, total_records_ingested = 0"
                + " WHERE source_type = 'MOCK_OPENPHISH'");
    }

    @Test
    void everyAuditActionHasAPathThatActuallyWritesARow() throws Exception {
        AuthSession admin = premium("audit-admin", RoleCode.SYSTEM_ADMIN);
        AuthSession user = premium("audit-user", RoleCode.TENANT_ADMIN);

        driveAuthenticationActions();
        driveIocActions(user);
        driveExportAndSyncActions(user);
        driveWebhookAndApiKeyActions(user);
        driveAdminActions(admin, user);
        driveIngestionActions();

        assertThat(probe.recordedActions()).containsAll(EnumSet.allOf(AuditAction.class));
    }

    /** LOGIN / LOGIN_FAILED / LOGOUT / TOKEN_REFRESH / TOKEN_REUSE_DETECTED / TENANT_CREATED / USER_CREATED。 */
    private void driveAuthenticationActions() throws Exception {
        String email = "audit-auth@example.org";
        mvc.perform(anonymous(post("/api/v1/auth/register"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email, "\"displayName\":\"Audit\"")));

        String refreshToken = loginAndReturnRefreshToken(email);
        mvc.perform(anonymous(post("/api/v1/auth/login"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"definitely-not-the-password\"}"));

        String rotated = refresh(refreshToken);
        // 舊枚重放 → 不變量 U5 的重用偵測 → TOKEN_REUSE_DETECTED
        refresh(refreshToken);
        mvc.perform(anonymous(post("/api/v1/auth/logout"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + rotated + "\"}"));
    }

    /** IOC_QUERY / IOC_DOWNLOAD / IOC_SUBMIT / IOC_IMPORT / IOC_REPORT_FP / API_ACCESS。 */
    private void driveIocActions(AuthSession user) throws Exception {
        String created = mvc.perform(authorized(post("/api/v1/iocs"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DOMAIN\",\"value\":\"audit-probe.example.org\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String indicatorId = json.readTree(created).get("id").asString();

        mvc.perform(authorized(get("/api/v1/iocs?limit=1"), user));
        mvc.perform(authorized(get("/api/v1/iocs/" + indicatorId + "/sources"), user));
        mvc.perform(authorized(post("/api/v1/iocs/" + indicatorId + "/report-false-positive"), user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"audit completeness probe\"}"));
        mvc.perform(authorized(post("/api/v1/iocs/import"), user)
                .contentType("text/csv")
                .content("type,value\nDOMAIN,audit-import.example.org\n"));
    }

    /** STIX_EXPORT / SYNC_MANIFEST / SYNC_BLOOM / SYNC_DELTA(狀態碼不影響觸發點是否成立)。 */
    private void driveExportAndSyncActions(AuthSession user) throws Exception {
        mvc.perform(authorized(get("/api/v1/stix/bundle?limit=1"), user));
        mvc.perform(authorized(get("/api/v1/sync/manifest"), user));
        mvc.perform(authorized(get("/api/v1/sync/bloom?scope=PUBLIC"), user));
        mvc.perform(authorized(get("/api/v1/sync/delta?base=0&scope=PUBLIC"), user));
    }

    /** WEBHOOK_CREATED / WEBHOOK_DELETED / API_KEY_CREATED / API_KEY_REVOKED。 */
    private void driveWebhookAndApiKeyActions(AuthSession user) throws Exception {
        String webhook = mvc.perform(authorized(post("/api/v1/webhooks"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"audit","targetUrl":"https://hooks.ctip-sample.invalid/audit",\
                                "eventTypes":["NEW_IOC"]}"""))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        mvc.perform(authorized(
                delete("/api/v1/webhooks/"
                        + json.readTree(webhook).at("/webhook/id").asString()),
                user));

        String apiKey = mvc.perform(authorized(post("/api/v1/api-keys"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"audit\",\"scopes\":[\"ioc:read\"]}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        mvc.perform(authorized(
                delete("/api/v1/api-keys/"
                        + json.readTree(apiKey).at("/apiKey/id").asString()),
                user));
    }

    /** ADMIN_ACTION / SUBSCRIPTION_CHANGED。 */
    private void driveAdminActions(AuthSession admin, AuthSession target) throws Exception {
        mvc.perform(authorized(get("/api/v1/admin/tenants"), admin));
        mvc.perform(authorized(
                        patch("/api/v1/admin/tenants/"
                                + target.identity().tenantId().value() + "/subscription"),
                        admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planCode\":\"ENTERPRISE\"}"));
    }

    /**
     * INGESTION_STARTED / INGESTION_COMPLETED / INGESTION_FAILED(排程路徑,非請求路徑)。
     *
     * <p>三條都直接走 {@link SourceSyncRecorder}——它就是 §13.5 指定的觸發點
     * (來源開始/結束處理),而在這裡真的跑一次同步有兩個問題:
     * <ul>
     *   <li>會把 mock feed 的資料灌進共用的測試資料庫,{@code IngestionEndToEndTest}
     *       的「第一次同步的收穫」斷言就會變成第二次同步的數字(實測過);</li>
     *   <li>mock adapter 是<strong>確定性</strong>的(08 §8.3:零亂數、固定資料集),
     *       沒有任何輸入能讓它失敗,{@code INGESTION_FAILED} 本來就驅動不出來——
     *       真實環境的失敗是網路。</li>
     * </ul>
     * 「同步流程確實會呼叫 recorder」由 {@code IngestionEndToEndTest} 覆蓋。
     */
    private void driveIngestionActions() {
        Source source = source(SourceType.MOCK_OPENPHISH);

        SourceSyncRecorder.SyncRun completed = recorder.started(source.id());
        recorder.completed(source, completed, 0, BatchOutcome.EMPTY, null);

        SourceSyncRecorder.SyncRun failed = recorder.started(source.id());
        recorder.failed(source, failed, 0, BatchOutcome.EMPTY, new IllegalStateException("audit completeness probe"));
    }

    private Source source(SourceType type) {
        return sources.findBySourceType(type).orElseThrow();
    }

    private String loginAndReturnRefreshToken(String email) throws Exception {
        String body = mvc.perform(anonymous(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, null)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(body).get("refreshToken").asString();
    }

    private String refresh(String refreshToken) throws Exception {
        String body = mvc.perform(anonymous(post("/api/v1/auth/refresh"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.contains("refreshToken")
                ? json.readTree(body).get("refreshToken").asString()
                : refreshToken;
    }

    private static String credentials(String email, String extra) {
        String base = "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"";
        return extra == null ? base + "}" : base + "," + extra + "}";
    }

    private AuthSession premium(String slug, RoleCode role) {
        AuthSession session = identities.register(slug + "@example.org", role);
        new TestPlans(plans, subscriptions, idGenerator, clock)
                .assign(session.identity().tenantId(), PlanCode.ENTERPRISE);
        return identities.login(slug + "@example.org");
    }

    private static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder builder, AuthSession as) {
        return builder.with(fromClient()).header("Authorization", TestIdentities.bearer(as));
    }

    private static MockHttpServletRequestBuilder anonymous(MockHttpServletRequestBuilder builder) {
        return builder.with(fromClient());
    }

    private static RequestPostProcessor fromClient() {
        return request -> {
            request.setRemoteAddr(CLIENT_IP);
            return request;
        };
    }
}
