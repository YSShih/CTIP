package com.ctip.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.ctip.application.identity.AuthSession;
import com.github.luben.zstd.ZstdInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 走 HTTP 的同步 client 替身(docs/spec/11-sync-bloom.md §11.6)。
 *
 * <p>存在的理由是「client 端」的動作要看得出來:解壓、驗 checksum、base64url 解碼、套用 delta
 * ——這些都是 §11.6 明列的步驟,散在測試方法裡會看不出流程。
 *
 * <p>每個實例綁一組(client IP, 身分):IP 決定匿名限流與同步節流的記帳對象(§10.7 維度 4),
 * 因此不同測試必須用不同 IP——限流器與節流狀態都在記憶體中跨測試類共用。
 */
public final class SyncTestClient {

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final String clientIp;
    private final String authorization;

    private SyncTestClient(MockMvc mvc, ObjectMapper json, String clientIp, String authorization) {
        this.mvc = mvc;
        this.json = json;
        this.clientIp = clientIp;
        this.authorization = authorization;
    }

    public static SyncTestClient anonymous(MockMvc mvc, ObjectMapper json, String clientIp) {
        return new SyncTestClient(mvc, json, clientIp, null);
    }

    public static SyncTestClient of(MockMvc mvc, ObjectMapper json, String clientIp, AuthSession session) {
        return new SyncTestClient(mvc, json, clientIp, TestIdentities.bearer(session));
    }

    public ResultActions manifestRequest() throws Exception {
        return perform(get("/api/v1/sync/manifest"));
    }

    public ResultActions bloomRequest(String query) throws Exception {
        return perform(get("/api/v1/sync/bloom" + query));
    }

    public ResultActions deltaRequest(String query) throws Exception {
        return perform(get("/api/v1/sync/delta" + query));
    }

    /** §11.6 第 1 步。 */
    public JsonNode manifest() throws Exception {
        return body(manifestRequest());
    }

    /** §11.6 第 3 步。 */
    public JsonNode delta(long base, String scope) throws Exception {
        return body(deltaRequest("?base=" + base + "&scope=" + scope));
    }

    /** §11.6 第 4 步;回傳原始回應,呼叫端要驗標頭。 */
    public MockHttpServletResponse bloom(String scope) throws Exception {
        MockHttpServletResponse response =
                bloomRequest("?scope=" + scope).andReturn().getResponse();
        assertThat(response.getStatus()).as("下載 full snapshot").isEqualTo(200);
        return response;
    }

    /** client 端的解壓:manifest 的 {@code compression} 就是在說要用哪個解碼器(§11.4「僅影響傳輸」)。 */
    public static byte[] inflateZstd(byte[] body) {
        try (ZstdInputStream in = new ZstdInputStream(new ByteArrayInputStream(body))) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("解壓 bloom artifact 失敗", e);
        }
    }

    /** §11.5 第 4 步的反向:base64url(無 padding)→ varint 差分 payload。 */
    public static byte[] decodeAddedBits(String addedBits) {
        assertThat(addedBits).as("base64url 無 padding(§11.5 第 4 步)").doesNotContain("=", "+", "/");
        return Base64.getUrlDecoder().decode(addedBits);
    }

    private JsonNode body(ResultActions actions) throws Exception {
        MockHttpServletResponse response = actions.andReturn().getResponse();
        assertThat(response.getStatus()).as("%s", response.getContentAsString()).isEqualTo(200);
        return json.readTree(response.getContentAsString());
    }

    private ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
        builder.with(request -> {
            request.setRemoteAddr(clientIp);
            return request;
        });
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        return mvc.perform(builder);
    }
}
