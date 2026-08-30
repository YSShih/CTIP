package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctip.application.identity.AuthService;
import com.ctip.application.identity.AuthSession;
import com.ctip.application.port.TenantMembershipRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.audit.AuditAction;
import com.ctip.infrastructure.audit.AuditWriter;
import com.ctip.support.AuditProbe;
import com.ctip.support.TestIdentities;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 稽核列的內容(docs/spec/04-data-dictionary.md 表 27):
 * 環境欄位(ip / user_agent / traceId)與行為者必須被補齊,而 metadata 不得含憑證
 * (§13.5 規則 5)。空殼的稽核軌跡在事後調查時等於沒有。
 *
 * <p>{@code GET /api/v1/audit-logs} 的可見範圍也在這裡驗:只看得到自己租戶的軌跡。
 */
@AutoConfigureMockMvc
class AuditRecordContentTest extends AbstractPostgresIntegrationTest {

    private static final String CLIENT_IP = "10.90.0.21";
    private static final String USER_AGENT = "ctip-audit-probe/1.0";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private TenantMembershipRepository memberships;

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

    @Test
    void aRecordedRowCarriesTheRequestContext() throws Exception {
        AuthSession user = identities.register("audit-context@example.org", RoleCode.TENANT_ADMIN);

        mvc.perform(authorized(get("/api/v1/iocs?limit=1"), user)).andExpect(status().isOk());

        assertThat(probe.latestColumn(AuditAction.IOC_QUERY, "ip")).isEqualTo(CLIENT_IP);
        assertThat(probe.latestColumn(AuditAction.IOC_QUERY, "user_agent")).isEqualTo(USER_AGENT);
        assertThat(probe.latestColumn(AuditAction.IOC_QUERY, "trace_id")).isNotBlank();
        assertThat(probe.latestColumn(AuditAction.API_ACCESS, "actor_type")).isEqualTo("USER");
        assertThat(probe.latestColumn(AuditAction.API_ACCESS, "actor_id")).isNotBlank();
    }

    /** §13.5 規則 5:憑證絕不進 metadata——連「記下了整個 Authorization 標頭」都不行。 */
    @Test
    void theMetadataNeverContainsTheCallersCredentials() throws Exception {
        AuthSession user = identities.register("audit-metadata@example.org", RoleCode.TENANT_ADMIN);

        mvc.perform(authorized(get("/api/v1/iocs?limit=1"), user)).andExpect(status().isOk());

        assertThat(probe.latestColumn(AuditAction.API_ACCESS, "metadata"))
                .doesNotContain(user.accessToken())
                .doesNotContain("Bearer ")
                .contains("/api/v1/iocs");
    }

    /** 登入是匿名端點,但成功之後那一列必須指得出是誰(AuditSignals 的用途)。 */
    @Test
    void aSuccessfulLoginRecordsTheUserThatLoggedIn() throws Exception {
        AuthSession user = identities.register("audit-login@example.org", RoleCode.TENANT_ADMIN);

        mvc.perform(anonymous(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"audit-login@example.org\",\"password\":\"" + TestIdentities.PASSWORD
                                + "\"}"))
                .andExpect(status().isOk());

        assertThat(probe.latestColumn(AuditAction.LOGIN, "actor_id"))
                .isEqualTo(user.identity().userId().value().toString());
    }

    /** 稽核軌跡是租戶範圍的:別的租戶做了什麼,這裡看不到(09 §9.1)。 */
    @Test
    void theEndpointOnlyReturnsTheCallersOwnTenantTrail() throws Exception {
        AuthSession stranger = identities.register("audit-stranger@example.org", RoleCode.TENANT_ADMIN);
        AuthSession viewer = identities.register("audit-viewer@example.org", RoleCode.TENANT_ADMIN);
        mvc.perform(authorized(get("/api/v1/iocs?limit=1"), stranger)).andExpect(status().isOk());
        probe.awaitWrites();

        mvc.perform(authorized(get("/api/v1/audit-logs?action=IOC_QUERY"), viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].actorId")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.not(
                                stranger.identity().userId().value().toString()))));
    }

    @Test
    void anUnknownActionFilterIsRejected() throws Exception {
        AuthSession user = identities.register("audit-filter@example.org", RoleCode.TENANT_ADMIN);

        mvc.perform(authorized(get("/api/v1/audit-logs?action=NOT_AN_ACTION"), user))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anonymousCallersCannotReadTheAuditTrail() throws Exception {
        mvc.perform(get("/api/v1/audit-logs").with(fromClient())).andExpect(status().isForbidden());
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
            request.addHeader("User-Agent", USER_AGENT);
            return request;
        };
    }
}
