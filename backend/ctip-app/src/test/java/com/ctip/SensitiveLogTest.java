package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctip.application.identity.ApiKeyIssueRequest;
import com.ctip.application.identity.ApiKeyService;
import com.ctip.application.identity.AuthService;
import com.ctip.application.identity.AuthSession;
import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.application.port.TenantMembershipRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.config.CtipProperties;
import com.ctip.domain.identity.IssuedApiKey;
import com.ctip.domain.identity.ScopeSet;
import com.ctip.infrastructure.observability.SensitiveMasks;
import com.ctip.support.LogCapture;
import com.ctip.support.LoggingFormats;
import com.ctip.support.TestIdentities;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * DoD M3-15 / 14 §14.4 條 8:日誌不含密碼、JWT secret、API key 原文、refresh token 原文、
 * {@code Authorization} 與 {@code X-API-Key} 的值(docs/spec/13-platform-ops.md §13.6「絕不記錄」)。
 *
 * <p>兩道防線各驗一次:第一道是「不把憑證交給 logger」(走真實的註冊 / 登入 / API key 流程,
 * 檢查整個 root logger 的輸出);第二道是遮罩——刻意把憑證寫進日誌,檢查<strong>輸出端</strong>
 * (JSON encoder 與純文字 pattern)把它們遮掉。只驗第一道的話,任何新增的日誌點都可能重新開洞。
 */
@AutoConfigureMockMvc
class SensitiveLogTest extends AbstractPostgresIntegrationTest {

    private static final String CLIENT_IP = "10.100.0.12";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private TenantMembershipRepository memberships;

    @Autowired
    private ApiKeyService apiKeys;

    @Autowired
    private CtipProperties properties;

    @Test
    void credentialsNeverReachTheLogs() throws Exception {
        try (LogCapture logs = LogCapture.start()) {
            AuthSession session =
                    new TestIdentities(authService, memberships).register("masked@example.org", RoleCode.TENANT_ADMIN);
            IssuedApiKey issued = issueKey(session);

            mvc.perform(asClient(get("/api/v1/iocs?limit=1")).header("X-API-Key", issued.plaintext()))
                    .andExpect(status().isOk());
            mvc.perform(asClient(get("/api/v1/api-keys")).header("Authorization", TestIdentities.bearer(session)))
                    .andExpect(status().isOk());
            // 401 路徑會記日誌,而它拿到的正是憑證本身
            mvc.perform(asClient(get("/api/v1/api-keys")).header("Authorization", "Bearer " + session.accessToken()))
                    .andExpect(status().isOk());

            assertThat(logs.text())
                    .doesNotContain(TestIdentities.PASSWORD)
                    .doesNotContain(properties.jwt().secret())
                    .doesNotContain(issued.plaintext())
                    .doesNotContain(session.refreshToken())
                    .doesNotContain(session.accessToken());
        }
    }

    /** 第二道防線:即使有人真的把憑證寫進日誌,輸出端也必須遮掉。 */
    @Nested
    class MaskingAtTheOutput {

        @Test
        void theJsonEncoderMasksEveryCredentialShape() {
            String json = LoggingFormats.encodeAsJson(leakyMessage());

            assertThat(json).doesNotContain("aB3dE5gH7jK9mN1pQ3sT5vW7yZ9bD1fH3jL5nP7r");
            assertThat(json).doesNotContain("ctip_mvp_0123456789abcdefABCDEF0123456789");
            assertThat(json).doesNotContain("test-password-1234");
            assertThat(json).contains(SensitiveMasks.MASK);
        }

        /** 走**實際生效的** appender:這同時驗證 logback-spring.xml 的 %mask 轉換規則真的掛上了。 */
        @Test
        void theConfiguredConsoleAppenderMasksEveryCredentialShape() {
            String line = LoggingFormats.encodeWithConfiguredAppender(leakyMessage());

            assertThat(line).doesNotContain("aB3dE5gH7jK9mN1pQ3sT5vW7yZ9bD1fH3jL5nP7r");
            assertThat(line).doesNotContain("ctip_mvp_0123456789abcdefABCDEF0123456789");
            assertThat(line).doesNotContain("test-password-1234");
            assertThat(line).contains(SensitiveMasks.MASK);
        }

        private String leakyMessage() {
            return "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2lnbmF0dXJl "
                    + "X-API-Key: ctip_mvp_0123456789abcdefABCDEF0123456789 "
                    + "refreshToken=aB3dE5gH7jK9mN1pQ3sT5vW7yZ9bD1fH3jL5nP7r "
                    + "{\"password\":\"test-password-1234\"}";
        }
    }

    private IssuedApiKey issueKey(AuthSession session) {
        AuthenticatedIdentity identity = session.identity();
        return apiKeys.issue(
                new ApiKeyIssueRequest(
                        identity.tenantId(), identity.userId(), "log-probe", new ScopeSet(Set.of("ioc:read")), null),
                identity);
    }

    private static MockHttpServletRequestBuilder asClient(MockHttpServletRequestBuilder builder) {
        return builder.with(request -> {
            request.setRemoteAddr(CLIENT_IP);
            return request;
        });
    }
}
