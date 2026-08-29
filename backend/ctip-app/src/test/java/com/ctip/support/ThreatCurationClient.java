package com.ctip.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctip.application.identity.AuthSession;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Threat 策展端點的測試 client(§9.1「Threat」與「Threat — 寫入」)。
 *
 * <p>把 HTTP 細節收在這裡,測試類只留情境。client IP 由呼叫端指定:Phase 17 起匿名的 write
 * 類別上限只有 12/min,每個測試類必須用自己的一組 IP,不與其他測試類共用額度。
 */
public final class ThreatCurationClient {

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final String clientIp;
    private final String anonymousIp;

    public ThreatCurationClient(MockMvc mvc, ObjectMapper json, String clientIp, String anonymousIp) {
        this.mvc = mvc;
        this.json = json;
        this.clientIp = clientIp;
        this.anonymousIp = anonymousIp;
    }

    /** 建立威脅;{@code tlp} 為 null 時採預設(AMBER,私有),給 CLEAR/GREEN 需要 ioc:publish。 */
    public JsonNode createThreat(AuthSession actor, String type, String name, String aliases, String tlp)
            throws Exception {
        return json.readTree(body(mvc.perform(as(
                        post("/api/v1/threats")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody(type, name, aliases, tlp)),
                        actor))
                .andExpect(status().isCreated())));
    }

    public void createThreatExpecting(AuthSession actor, String type, String name, int expectedStatus)
            throws Exception {
        mvc.perform(as(
                        post("/api/v1/threats")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody(type, name, "[]", null)),
                        actor))
                .andExpect(status().is(expectedStatus));
    }

    public void createThreatAnonymouslyExpecting(String type, String name, int expectedStatus) throws Exception {
        mvc.perform(anonymous(post("/api/v1/threats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(type, name, "[]", null))))
                .andExpect(status().is(expectedStatus));
    }

    public JsonNode link(AuthSession actor, String threatId, String iocId, String role) throws Exception {
        return json.readTree(body(mvc.perform(as(
                        put("/api/v1/threats/" + threatId + "/indicators/" + iocId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"role\":\"" + role + "\"}"),
                        actor))
                .andExpect(status().isOk())));
    }

    public JsonNode unlink(AuthSession actor, String threatId, String iocId) throws Exception {
        return json.readTree(body(mvc.perform(as(delete("/api/v1/threats/" + threatId + "/indicators/" + iocId), actor))
                .andExpect(status().isOk())));
    }

    public void addReference(AuthSession actor, String threatId, String requestBody, int expectedStatus)
            throws Exception {
        mvc.perform(as(
                        post("/api/v1/threats/" + threatId + "/external-references")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody),
                        actor))
                .andExpect(status().is(expectedStatus));
    }

    public void changeStatus(AuthSession actor, String threatId, String status, int expectedStatus) throws Exception {
        mvc.perform(as(
                        put("/api/v1/threats/" + threatId + "/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"" + status + "\"}"),
                        actor))
                .andExpect(status().is(expectedStatus));
    }

    /** 手動提交:預設 AMBER 且歸屬提交者租戶(§9.7)。 */
    public String submitIoc(AuthSession actor, String value) throws Exception {
        String response = body(mvc.perform(as(
                        post("/api/v1/iocs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"type\":\"DOMAIN\",\"value\":\"" + value + "\"}"),
                        actor))
                .andExpect(status().isCreated()));
        return json.readTree(response).get("id").asString();
    }

    public JsonNode getAnonymously(String uri) throws Exception {
        return json.readTree(body(mvc.perform(anonymous(get(uri))).andExpect(status().isOk())));
    }

    public void expectStatusAsUser(AuthSession actor, String uri, int expectedStatus) throws Exception {
        mvc.perform(as(get(uri), actor)).andExpect(status().is(expectedStatus));
    }

    public void expectStatusAnonymously(String uri, int expectedStatus) throws Exception {
        mvc.perform(anonymous(get(uri))).andExpect(status().is(expectedStatus));
    }

    private static String createBody(String type, String name, String aliases, String tlp) {
        return "{\"type\":\"" + type + "\",\"name\":\"" + name + "\",\"aliases\":" + aliases
                + ",\"severity\":\"HIGH\",\"confidence\":70"
                + (tlp == null ? "" : ",\"tlp\":\"" + tlp + "\"")
                + ",\"tags\":[\"phase18\"]}";
    }

    private static String body(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, AuthSession session) {
        return builder.header("Authorization", TestIdentities.bearer(session)).with(request -> {
            request.setRemoteAddr(clientIp);
            return request;
        });
    }

    private MockHttpServletRequestBuilder anonymous(MockHttpServletRequestBuilder builder) {
        return builder.with(request -> {
            request.setRemoteAddr(anonymousIp);
            return request;
        });
    }
}
