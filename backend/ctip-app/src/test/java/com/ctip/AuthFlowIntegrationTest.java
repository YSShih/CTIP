package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctip.application.port.UserRepository;
import com.ctip.domain.user.EmailAddress;
import com.ctip.support.TestIdentities;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * DoD M2-02:註冊 → 登入 → refresh → 登出全流程(docs/spec/09-api.md §9.1、10 §10.4)。
 * 一併驗證 JWT claims 不含個資、註冊建立的租戶不是 public tenant(不變量 U2)。
 */
@AutoConfigureMockMvc
class AuthFlowIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String EMAIL = "authflow@example.org";
    private static final String CLIENT_IP = "10.20.0.11";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UserRepository users;

    @Test
    void registerIssuesASessionAndRejectsDuplicateEmail() throws Exception {
        JsonNode registered = perform(
                post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Flow Tester","tenantName":"Flow Org"}""".formatted(EMAIL, TestIdentities.PASSWORD)),
                201);
        assertThat(registered.get("tokenType").asString()).isEqualTo("Bearer");
        assertThat(registered.get("expiresIn").asLong()).isPositive();
        assertThat(registered.at("/user/role").asString()).isEqualTo("TENANT_ADMIN");
        // 不變量 U2:註冊建立的租戶不是 public tenant
        assertThat(registered.at("/user/tenantId").asString()).isNotEqualTo("00000000-0000-0000-0000-000000000000");

        // 不變量 U1:同一 email 不得重複註冊
        perform(
                post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(EMAIL, TestIdentities.PASSWORD)),
                409);
    }

    @Test
    void loginRefreshAndLogoutRotateThenRevokeTheSession() throws Exception {
        String email = "flow-session@example.org";
        perform(
                post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(email, TestIdentities.PASSWORD)),
                201);

        JsonNode loggedIn = perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(email, TestIdentities.PASSWORD)),
                200);
        assertThat(loggedIn.at("/user/permissions")).isNotEmpty();
        mvc.perform(asClient(get("/api/v1/api-keys"))
                        .header(
                                "Authorization",
                                "Bearer " + loggedIn.get("accessToken").asString()))
                .andExpect(status().isOk());

        String refreshToken = loggedIn.get("refreshToken").asString();
        JsonNode refreshed = perform(
                post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}""".formatted(refreshToken)),
                200);
        String rotated = refreshed.get("refreshToken").asString();
        assertThat(rotated).isNotEqualTo(refreshToken);

        mvc.perform(asClient(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}""".formatted(rotated))))
                .andExpect(status().isNoContent());
        // 登出後該 family 全撤,最新一枚也不能再輪替
        mvc.perform(asClient(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}""".formatted(rotated))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void accessTokenCarriesNoPersonalData() throws Exception {
        String email = "claims@example.org";
        JsonNode session = perform(
                post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Claims Tester"}""".formatted(email, TestIdentities.PASSWORD)),
                201);
        String payload = new String(
                Base64.getUrlDecoder()
                        .decode(session.get("accessToken").asString().split("\\.")[1]),
                StandardCharsets.UTF_8);
        assertThat(payload).doesNotContain(email).doesNotContain("Claims Tester");
        assertThat(json.readTree(payload).propertyNames())
                .containsExactlyInAnyOrder("sub", "tid", "roles", "perms", "iat", "exp", "jti");
        assertThat(users.findByEmail(new EmailAddress(email))).isPresent();
    }

    @Test
    void invalidCredentialsAreRejectedWithoutRevealingAccountExistence() throws Exception {
        mvc.perform(asClient(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nobody@example.org","password":"%s"}""".formatted(TestIdentities.PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    private JsonNode perform(MockHttpServletRequestBuilder builder, int expectedStatus) throws Exception {
        String response = mvc.perform(asClient(builder))
                .andExpect(status().is(expectedStatus))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(response);
    }

    /** 各測試類使用獨立 client IP,避免共用匿名限流 bucket。 */
    private static MockHttpServletRequestBuilder asClient(MockHttpServletRequestBuilder builder) {
        return builder.with(request -> {
            request.setRemoteAddr(CLIENT_IP);
            return request;
        });
    }
}
