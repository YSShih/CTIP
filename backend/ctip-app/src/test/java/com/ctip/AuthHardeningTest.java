package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctip.application.identity.AuthService;
import com.ctip.application.port.TenantMembershipRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.support.TestIdentities;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 13 收尾稽核的三項認證層加固(ADR 0013):
 *
 * <ol>
 *   <li>登入鎖定的回應不得與「帳號不存在」有差異——否則連送 10 次錯密碼就能列舉帳號,
 *       直接抵銷 ADR 0012 決策 17 才修掉的時間側信道
 *   <li>密碼上限對齊 BCrypt 的 72 bytes,並給出欄位級訊息
 *   <li>{@code Authorization} 的 auth-scheme 依 RFC 7235 大小寫不敏感;非 Bearer 一律 401,
 *       不得靜默降級為匿名
 * </ol>
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthHardeningTest extends AbstractPostgresIntegrationTest {

    /**
     * 每個測試方法一個 client IP。Phase 17 起限流多了維度 5(端點類別):匿名的 write 上限是
     * 總配額的 20%(60/min → <strong>12/min</strong>,ADR 0020),而本類光是鎖定測試就送 12 次
     * 登入 POST——共用同一個 IP 會讓後面的方法拿到 429 而不是它要斷言的狀態碼。
     */
    private static final String LOCKOUT_IP = "10.20.0.22";

    private static final String REGISTER_IP = "10.20.0.23";

    private static final String SESSION_IP = "10.20.0.24";
    private static final String LOCK_EMAIL = "lockout-oracle@example.org";
    private static final String SESSION_EMAIL = "scheme-probe@example.org";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private AuthService authService;

    @Autowired
    private TenantMembershipRepository memberships;

    private TestIdentities identities;

    /** 兩個帳號分開:鎖定測試會把 LOCK_EMAIL 鎖住,不能影響需要成功登入的測試。 */
    @BeforeAll
    void registerAccounts() {
        identities = new TestIdentities(authService, memberships);
        identities.register(LOCK_EMAIL, RoleCode.TENANT_ADMIN);
        identities.register(SESSION_EMAIL, RoleCode.TENANT_ADMIN);
    }

    /** 鎖定後的回應與帳號不存在的回應必須逐欄位相同(status / code / message)。 */
    @Test
    void lockedAccountIsIndistinguishableFromAnUnknownOne() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            login(LOCK_EMAIL, "definitely-wrong-password");
        }

        JsonNode locked = login(LOCK_EMAIL, "definitely-wrong-password");
        // 第 12 次已用盡 write 類別的配額,未知帳號改由另一個 IP 送(比對的是回應內容,與來源無關)
        JsonNode unknown = login("no-such-account@example.org", "definitely-wrong-password", REGISTER_IP);

        assertThat(locked.get("status")).isEqualTo(unknown.get("status"));
        assertThat(locked.get("code")).isEqualTo(unknown.get("code"));
        assertThat(locked.get("message"))
                .as("鎖定與帳號不存在的訊息若可區分,連送 10 次錯密碼即可列舉出已註冊的 email")
                .isEqualTo(unknown.get("message"));
    }

    /** 超過 BCrypt 上限的密碼要在欄位層被擋下,而不是變成一則沒有說明的 400。 */
    @Test
    void passwordBeyondBcryptLimitIsRejectedWithFieldDetail() throws Exception {
        String tooLong = "a".repeat(73);
        String body = mvc.perform(asClient(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"too-long@example.org\",\"password\":\"" + tooLong + "\"}"),
                        REGISTER_IP))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(json.readTree(body).get("details").toString()).contains("password");
    }

    /** 72 bytes 剛好可用:上限是位元組數,不是字元數。 */
    @Test
    void passwordAtExactlySeventyTwoBytesIsAccepted() throws Exception {
        mvc.perform(asClient(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"exactly-72@example.org\",\"password\":\"" + "b".repeat(72)
                                        + "\"}"),
                        REGISTER_IP))
                .andExpect(status().isCreated());
    }

    /** 小寫 scheme 必須被當成 Bearer 處理,而不是無聲降級為匿名。 */
    @Test
    void lowercaseBearerSchemeIsAcceptedAsBearer() throws Exception {
        String accessToken = identities.login(SESSION_EMAIL).accessToken();
        mvc.perform(asClient(get("/api/v1/api-keys").header("Authorization", "bearer " + accessToken), SESSION_IP))
                .andExpect(status().isOk());
    }

    /** 非 Bearer 的 scheme 是無效憑證,回 401;舊實作會綁匿名並回 200。 */
    @Test
    void nonBearerSchemeIsRejectedInsteadOfDowngradedToAnonymous() throws Exception {
        mvc.perform(asClient(get("/api/v1/iocs?limit=1").header("Authorization", "Basic dXNlcjpwYXNz"), SESSION_IP))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    /** 對照組:完全沒有 Authorization 標頭仍是正當的匿名身分。 */
    @Test
    void absentAuthorizationHeaderRemainsAnonymous() throws Exception {
        mvc.perform(asClient(get("/api/v1/iocs?limit=1"), SESSION_IP)).andExpect(status().isOk());
    }

    private JsonNode login(String email, String password) throws Exception {
        return login(email, password, LOCKOUT_IP);
    }

    private JsonNode login(String email, String password, String clientIp) throws Exception {
        String body = mvc.perform(asClient(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"),
                        clientIp))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(body);
    }

    /** 獨立 client IP,避免與其他測試類(以及本類其他方法)共用匿名限流 bucket。 */
    private static MockHttpServletRequestBuilder asClient(MockHttpServletRequestBuilder builder, String clientIp) {
        return builder.with(request -> {
            request.setRemoteAddr(clientIp);
            return request;
        });
    }
}
