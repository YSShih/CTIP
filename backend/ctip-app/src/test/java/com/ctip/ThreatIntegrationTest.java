package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.identity.AuthService;
import com.ctip.application.identity.AuthSession;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.EventPublisherPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.IndicatorRepository;
import com.ctip.application.port.PlanRepository;
import com.ctip.application.port.SubscriptionRepository;
import com.ctip.application.port.TenantMembershipRepository;
import com.ctip.application.port.ThreatRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.threat.Threat;
import com.ctip.domain.threat.ThreatId;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.Tlp;
import com.ctip.support.PublishedIndicators;
import com.ctip.support.TestIdentities;
import com.ctip.support.TestPlans;
import com.ctip.support.ThreatCurationClient;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Threat 實體與關聯的端到端行為(phase-18 完成判準)。
 *
 * <p>覆蓋:寫入端點(ADR 0027 補上的建立管道)、H1／H3／H4 衝突、
 * <strong>H6</strong>(建立關聯時收緊、Indicator 事後收緊時連帶收緊、永不放寬)、
 * 三個讀取端點的可見度(含「關聯不是可見度的旁路」)、RETIRED 的終態語意,
 * 以及 STIX 投影({@code malware} + {@code relationship})經 {@code GET /stix/{stixId}} 可讀。
 *
 * <p>HTTP 細節在 {@link ThreatCurationClient};限流的 client IP 為本類專用的 {@code 10.50.0.x}。
 */
@AutoConfigureMockMvc
class ThreatIntegrationTest extends AbstractPostgresIntegrationTest {

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
    private IndicatorRepository indicators;

    @Autowired
    private ThreatRepository threats;

    @Autowired
    private EventPublisherPort eventPublisher;

    @Autowired
    private IdGeneratorPort idGenerator;

    @Autowired
    private ClockPort clock;

    private TestIdentities identities;
    private TestPlans planAdmin;
    private ThreatCurationClient api;
    private PublishedIndicators publishedIocs;

    @BeforeEach
    void setUp() {
        identities = new TestIdentities(authService, memberships);
        planAdmin = new TestPlans(plans, subscriptions, idGenerator, clock);
        api = new ThreatCurationClient(mvc, json, "10.50.0.11", "10.50.0.12");
        publishedIocs = new PublishedIndicators(indicators, eventPublisher);
    }

    @Test
    void publishedThreatIsReadableByAnonymous() throws Exception {
        AuthSession publisher = publisher("threat-create@example.org");

        JsonNode created = api.createThreat(publisher, "MALWARE_FAMILY", "AgentTesla-A", "[\"AT-A\"]", "CLEAR");

        assertThat(created.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(created.get("tlp").asString()).isEqualTo("CLEAR");
        assertThat(created.get("indicatorCount").asInt()).isZero();
        // 別名以 text[] 的 @> 過濾(必須走 cast(? as text[]),否則 operator does not exist)
        assertThat(api.getAnonymously("/api/v1/threats?aliases=AT-A")
                        .at("/items/0/name")
                        .asString())
                .isEqualTo("AgentTesla-A");
        assertThat(api.getAnonymously("/api/v1/threats/" + id(created))
                        .get("name")
                        .asString())
                .isEqualTo("AgentTesla-A");
    }

    /** H6:關聯一個較嚴格的 IOC,Threat 的 TLP 必須跟著收緊——且永不放寬。 */
    @Test
    void linkingATenantIocTightensTheThreatTlpAndHidesItFromAnonymous() throws Exception {
        AuthSession publisher = publisher("threat-h6@example.org");
        String threatId = id(api.createThreat(publisher, "MALWARE_FAMILY", "AgentTesla-H6", "[]", "CLEAR"));
        String iocId = api.submitIoc(publisher, "h6-private.example.org"); // 手動提交預設 AMBER

        JsonNode linked = api.link(publisher, threatId, iocId, "C2");

        assertThat(linked.get("tlp").asString()).isEqualTo("AMBER");
        assertThat(linked.get("indicatorCount").asInt()).isEqualTo(1);
        // TLP 收緊後,匿名再也看不到這個 threat(§7.7 可見度)
        api.expectStatusAnonymously("/api/v1/threats/" + threatId, 404);

        JsonNode unlinked = api.unlink(publisher, threatId, iocId);

        // 解除關聯不會放寬回 CLEAR:收緊是單向的
        assertThat(unlinked.get("tlp").asString()).isEqualTo("AMBER");
        assertThat(unlinked.get("indicatorCount").asInt()).isZero();
    }

    /**
     * ADR 0020 的事後維持:Indicator 的 TLP 在多來源合併時被收緊,
     * {@code IndicatorTlpTightened} 事件必須讓關聯的 Threat 一起收緊。
     */
    @Test
    void tighteningAnIndicatorLaterAlsoTightensTheLinkedThreat() throws Exception {
        AuthSession publisher = publisher("threat-retighten@example.org");
        String threatId = id(api.createThreat(publisher, "MALWARE_FAMILY", "AgentTesla-Re", "[]", "CLEAR"));
        String iocId = publicIoc(publisher, "retighten.example.org", RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        api.link(publisher, threatId, iocId, "INFRASTRUCTURE");
        assertThat(storedThreat(threatId).tlp()).isEqualTo(Tlp.CLEAR);

        publishedIocs.tightenToAmber(iocId);

        assertThat(storedThreat(threatId).tlp()).isEqualTo(Tlp.AMBER);
    }

    /**
     * 關聯不是可見度的旁路:關聯的每個 IOC 都要再走一次 IOC 的可見度(含再散布規則 3)。
     *
     * <p>用再散布這條軸來測——TLP 那條軸被 H6 蓋掉了(關聯私有 IOC 會把 threat 一起收緊),
     * 而「全來源 INTERNAL_ONLY」的公開 IOC 是 TLP 可見、再散布不可見,正好落在關聯清單這一層。
     */
    @Test
    void linkedIndicatorsAreFilteredByTheIndicatorVisibility() throws Exception {
        AuthSession publisher = publisher("threat-linkvis@example.org");
        String threatId = id(api.createThreat(publisher, "CAMPAIGN", "Operation-LinkVis", "[]", "CLEAR"));
        String shareable = publicIoc(publisher, "linkvis-a.example.org", RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        String restricted = publicIoc(publisher, "linkvis-b.example.org", RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        api.link(publisher, threatId, shareable, "DELIVERY");
        api.link(publisher, threatId, restricted, "PAYLOAD");
        // 來源條款會變(§7.9 規則 1):關聯建立之後,那筆 IOC 的來源政策轉為 INTERNAL_ONLY
        publishedIocs.publish(restricted, RedistributionPolicy.INTERNAL_ONLY);

        assertThat(api.getAnonymously("/api/v1/threats/" + threatId)
                        .get("indicatorCount")
                        .asInt())
                .isEqualTo(2);
        JsonNode anonymousLinks = api.getAnonymously("/api/v1/threats/" + threatId + "/indicators");
        assertThat(anonymousLinks).hasSize(1);
        assertThat(anonymousLinks.at("/0/ioc/id").asString()).isEqualTo(shareable);
        assertThat(anonymousLinks.at("/0/role").asString()).isEqualTo("DELIVERY");
    }

    /** 另一個租戶的私有威脅一律 404(不洩漏存在性)。 */
    @Test
    void anotherTenantsThreatIsNotFound() throws Exception {
        AuthSession owner = curator("threat-owner@example.org");
        String threatId = id(api.createThreat(owner, "MALWARE_FAMILY", "AgentTesla-Priv", "[]", null));
        AuthSession stranger = curator("threat-stranger@example.org");

        api.expectStatusAsUser(stranger, "/api/v1/threats/" + threatId, 404);
        api.expectStatusAnonymously("/api/v1/threats/" + threatId, 404);
    }

    @Test
    void identityAndExternalReferenceConflictsReturn409() throws Exception {
        AuthSession curator = curator("threat-conflict@example.org");
        String threatId = id(api.createThreat(curator, "MALWARE_FAMILY", "AgentTesla-Conf", "[]", null));

        api.createThreatExpecting(curator, "MALWARE_FAMILY", "AgentTesla-Conf", 409); // H1
        api.addReference(curator, threatId, "{\"sourceName\":\"mitre-attack\",\"externalId\":\"S0331\"}", 201);
        api.addReference(curator, threatId, "{\"sourceName\":\"mitre-attack\",\"externalId\":\"S0331\"}", 409); // H4
        // external_id 為 null 時也必須被 H4 擋住(COALESCE 唯一索引;PostgreSQL 的 UNIQUE 不去重 null)
        api.addReference(curator, threatId, "{\"sourceName\":\"blog\",\"url\":\"https://a.example.test\"}", 201);
        api.addReference(curator, threatId, "{\"sourceName\":\"blog\",\"url\":\"https://b.example.test\"}", 409);
        api.addReference(curator, threatId, "{\"sourceName\":\"empty\"}", 400); // H3
    }

    @Test
    void retiredIsTerminalAndExcludedFromTheDefaultListing() throws Exception {
        AuthSession publisher = publisher("threat-retire@example.org");
        String threatId = id(api.createThreat(publisher, "THREAT_ACTOR", "Actor-Retire", "[]", "CLEAR"));

        api.changeStatus(publisher, threatId, "DORMANT", 200);
        api.changeStatus(publisher, threatId, "RETIRED", 200);
        api.changeStatus(publisher, threatId, "ACTIVE", 409);

        assertThat(api.getAnonymously("/api/v1/threats?name=Actor-Retire")
                        .at("/items")
                        .size())
                .isZero();
        assertThat(api.getAnonymously("/api/v1/threats?name=Actor-Retire&includeRetired=true")
                        .at("/items/0/status")
                        .asString())
                .isEqualTo("RETIRED");
    }

    /** M2 的 STIX 物件:malware 與 relationship 都必須落庫,且經 GET /stix/{stixId} 讀得到。 */
    @Test
    void stixProjectionsAreWrittenForMalwareFamiliesAndTheirLinks() throws Exception {
        AuthSession publisher = publisher("threat-stix@example.org");
        String threatId = id(api.createThreat(publisher, "MALWARE_FAMILY", "AgentTesla-Stix", "[\"AT-S\"]", "CLEAR"));
        String iocId = publicIoc(publisher, "stix-public.example.org", RedistributionPolicy.PUBLIC_REDISTRIBUTABLE);
        api.link(publisher, threatId, iocId, "C2");

        JsonNode malware = api.getAnonymously("/api/v1/stix/malware--" + threatId);
        assertThat(malware.get("type").asString()).isEqualTo("malware");
        assertThat(malware.get("is_family").asBoolean()).isTrue();
        assertThat(malware.get("name").asString()).isEqualTo("AgentTesla-Stix");

        JsonNode relationship = api.getAnonymously("/api/v1/stix/" + relationshipStixId(threatId, iocId));
        assertThat(relationship.get("relationship_type").asString()).isEqualTo("indicates");
        assertThat(relationship.get("source_ref").asString()).isEqualTo("indicator--" + iocId);
        assertThat(relationship.get("target_ref").asString()).isEqualTo("malware--" + threatId);
    }

    /** 型別沒有 SDO 的威脅(CAMPAIGN 等)不產生 STIX 物件——不得留下投影不出的殼。 */
    @Test
    void campaignsHaveNoStixProjectionInM2() throws Exception {
        AuthSession publisher = publisher("threat-campaign@example.org");
        String threatId = id(api.createThreat(publisher, "CAMPAIGN", "Operation-NoSdo", "[]", "CLEAR"));

        api.expectStatusAnonymously("/api/v1/stix/campaign--" + threatId, 404);
    }

    @Test
    void writeEndpointsRequireThreatManage() throws Exception {
        AuthSession plainUser = identities.register("threat-plain@example.org", RoleCode.USER);

        api.createThreatExpecting(plainUser, "MALWARE_FAMILY", "AgentTesla-NoPerm", 403);
        api.createThreatAnonymouslyExpecting("MALWARE_FAMILY", "AgentTesla-Anon", 403);
    }

    /** 租戶層策展者:有 threat:manage,沒有 ioc:publish——只能建立自家租戶的私有威脅。 */
    private AuthSession curator(String email) {
        AuthSession session = identities.register(email, RoleCode.TENANT_ADMIN);
        planAdmin.assign(session.identity().tenantId(), PlanCode.PREMIUM);
        return session;
    }

    /** 平台策展者:另有 ioc:publish,才能把威脅發布到 public tenant(§9.7 的規則,ADR 0027 沿用)。 */
    private AuthSession publisher(String email) {
        AuthSession session = identities.register(email, RoleCode.SYSTEM_ADMIN);
        planAdmin.assign(session.identity().tenantId(), PlanCode.PREMIUM);
        return session;
    }

    /**
     * 公開的 CLEAR 情資,再散布政策可指定——{@code INTERNAL_ONLY} 用來測「TLP 可見但不可再散布」
     * 這條獨立的軸(feed 攝取的 INTERNAL_ONLY 資料就是這個形狀)。
     */
    private String publicIoc(AuthSession actor, String value, RedistributionPolicy policy) throws Exception {
        String iocId = api.submitIoc(actor, value);
        publishedIocs.publish(iocId, policy);
        return iocId;
    }

    private Threat storedThreat(String threatId) {
        return threats.findById(new ThreatId(UUID.fromString(threatId))).orElseThrow();
    }

    /** 與 {@code StixRelationshipProjector} 相同的決定性 id(重投影必須是 UPSERT)。 */
    private static String relationshipStixId(String threatId, String iocId) {
        String seed = "relationship:indicates:indicator--" + iocId + ":malware--" + threatId;
        return "relationship--" + UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private static String id(JsonNode threat) {
        return threat.get("id").asString();
    }
}
