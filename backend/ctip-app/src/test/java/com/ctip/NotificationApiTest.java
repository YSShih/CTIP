package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctip.application.identity.AuthService;
import com.ctip.application.identity.AuthSession;
import com.ctip.application.notification.NotificationService;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.PlanRepository;
import com.ctip.application.port.SubscriptionRepository;
import com.ctip.application.port.TenantMembershipRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.Severity;
import com.ctip.support.TestIdentities;
import com.ctip.support.TestPlans;
import com.ctip.support.WebhookFixtures;
import com.ctip.support.WebhookTestConfig;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 通知與 webhook 的五個 REST 端點(docs/spec/09-api.md §9.1「通知與稽核」與「即時推送」)。
 *
 * <p>重點在<strong>可見範圍與存在性</strong>:跨租戶的通知與 webhook 一律 404 而非 403,
 * 回 403 等於承認那個 id 存在。
 */
@AutoConfigureMockMvc
@Import(WebhookTestConfig.class)
class NotificationApiTest extends AbstractPostgresIntegrationTest {

    private static final String CLIENT_IP = "10.70.0.12";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationService notifications;

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

    private TestIdentities identities;
    private TestPlans testPlans;

    @BeforeEach
    void setUp() {
        identities = new TestIdentities(authService, memberships);
        testPlans = new TestPlans(plans, subscriptions, idGenerator, clock);
    }

    @Test
    void listsAndMarksNotificationsForTheCallersTenant() throws Exception {
        AuthSession session = premium("notif-list");
        TenantId tenantId = session.identity().tenantId();
        UUID eventId = idGenerator.nextId();
        notifications.dispatch(WebhookFixtures.newIoc(eventId, tenantId, Severity.HIGH, Set.of(), Set.of()));

        String body = mvc.perform(authorized(get("/api/v1/notifications?unreadOnly=true"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].eventType").value("NEW_IOC"))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode page = objectMapper.readTree(body);
        String id = page.at("/items/0/id").asString();

        mvc.perform(authorized(patch("/api/v1/notifications/" + id + "/read"), session))
                .andExpect(status().isNoContent());
        // 已讀之後不再出現在 unreadOnly 的結果裡,而且重複標記回 404
        mvc.perform(authorized(get("/api/v1/notifications?unreadOnly=true"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(authorized(patch("/api/v1/notifications/" + id + "/read"), session))
                .andExpect(status().isNotFound());
    }

    @Test
    void anotherTenantsNotificationIsNotVisibleAndCannotBeMarked() throws Exception {
        AuthSession owner = premium("notif-owner");
        AuthSession stranger = premium("notif-stranger");
        notifications.dispatch(WebhookFixtures.newIoc(
                idGenerator.nextId(), owner.identity().tenantId(), Severity.HIGH, Set.of(), Set.of()));

        String body = mvc.perform(authorized(get("/api/v1/notifications"), owner))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = objectMapper.readTree(body).at("/items/0/id").asString();

        mvc.perform(authorized(get("/api/v1/notifications"), stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(authorized(patch("/api/v1/notifications/" + id + "/read"), stranger))
                .andExpect(status().isNotFound());
    }

    @Test
    void anonymousCallersCannotReadNotifications() throws Exception {
        mvc.perform(get("/api/v1/notifications").with(fromClient())).andExpect(status().isForbidden());
    }

    @Test
    void createsListsAndDeletesAWebhookAndDisclosesTheSecretExactlyOnce() throws Exception {
        AuthSession session = premium("hook-crud");

        String created = mvc.perform(authorized(post("/api/v1/webhooks"), session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"soc","targetUrl":"https://hooks.ctip-sample.invalid/soc",\
                                "eventTypes":["NEW_IOC"],"filterIocTypes":["IPV4"],"filterMinSeverity":"HIGH"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.secret").isNotEmpty())
                .andExpect(jsonPath("$.webhook.status").value("ACTIVE"))
                .andExpect(jsonPath("$.webhook.filter.minSeverity").value("HIGH"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = objectMapper.readTree(created).at("/webhook/id").asString();

        // 讀取端點不得再吐出密鑰(不變量 W2 的對外契約)
        String listed = mvc.perform(authorized(get("/api/v1/webhooks"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(listed).doesNotContain("secret");

        mvc.perform(authorized(delete("/api/v1/webhooks/" + id), session)).andExpect(status().isNoContent());
        mvc.perform(authorized(delete("/api/v1/webhooks/" + id), session)).andExpect(status().isNotFound());
    }

    @Test
    void anotherTenantsWebhookIsReportedAsNotFound() throws Exception {
        AuthSession owner = premium("hook-owner");
        AuthSession stranger = premium("hook-stranger");
        String created = mvc.perform(authorized(post("/api/v1/webhooks"), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"owned","targetUrl":"https://hooks.ctip-sample.invalid/owned",\
                                "eventTypes":["NEW_IOC"]}"""))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = objectMapper.readTree(created).at("/webhook/id").asString();

        mvc.perform(authorized(get("/api/v1/webhooks"), stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(authorized(delete("/api/v1/webhooks/" + id), stranger)).andExpect(status().isNotFound());
    }

    /** 不變量 W1 在 DTO 層就擋下,回 400 而不是 500。 */
    @Test
    void aPlainHttpTargetIsRejectedWithFourHundred() throws Exception {
        AuthSession session = premium("hook-scheme");
        mvc.perform(authorized(post("/api/v1/webhooks"), session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"insecure","targetUrl":"http://hooks.ctip-sample.invalid/x",\
                                "eventTypes":["NEW_IOC"]}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownEventTypeIsRejectedWithFourHundred() throws Exception {
        AuthSession session = premium("hook-enum");
        mvc.perform(authorized(post("/api/v1/webhooks"), session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"bad","targetUrl":"https://hooks.ctip-sample.invalid/x",\
                                "eventTypes":["NOT_A_TYPE"]}"""))
                .andExpect(status().isBadRequest());
    }

    /** FREE 方案沒有 webhook 額度,也沒有即時推送:兩者都是非時間窗的能力上限 → 403。 */
    @Test
    void theFreePlanHasNeitherWebhooksNorRealtimePush() throws Exception {
        AuthSession free = identities.register("notif-free@example.org", RoleCode.TENANT_ADMIN);

        mvc.perform(authorized(post("/api/v1/webhooks"), free)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"free","targetUrl":"https://hooks.ctip-sample.invalid/free",\
                                "eventTypes":["NEW_IOC"]}"""))
                .andExpect(status().isForbidden());

        mvc.perform(authorized(get("/api/v1/events"), free)).andExpect(status().isForbidden());
    }

    /** SSE fallback 需要 notification:read + 方案的 websocket_enabled(09 §9.1、ADR 0029)。 */
    @Test
    void theSseStreamOpensForAPlanThatAllowsRealtimePush() throws Exception {
        AuthSession session = premium("notif-sse");
        mvc.perform(authorized(get("/api/v1/events"), session))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType())
                        .startsWith(MediaType.TEXT_EVENT_STREAM_VALUE));
    }

    @Test
    void anonymousCallersCannotOpenTheSseStream() throws Exception {
        mvc.perform(get("/api/v1/events").with(fromClient())).andExpect(status().isForbidden());
    }

    private AuthSession premium(String slug) {
        AuthSession session = identities.register(slug + "@example.org", RoleCode.TENANT_ADMIN);
        testPlans.assign(session.identity().tenantId(), PlanCode.PREMIUM);
        return session;
    }

    private static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder builder, AuthSession as) {
        return builder.with(fromClient()).header("Authorization", TestIdentities.bearer(as));
    }

    private static RequestPostProcessor fromClient() {
        return request -> {
            request.setRemoteAddr(CLIENT_IP);
            return request;
        };
    }
}
