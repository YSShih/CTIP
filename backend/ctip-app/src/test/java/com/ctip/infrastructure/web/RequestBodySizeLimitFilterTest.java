package com.ctip.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.port.ClockPort;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 請求本文的硬上限(§9.7 的 413 必須在資料進到堆積之前發生)。
 *
 * <p>{@code @RequestBody byte[]} 會先把整包讀進記憶體才輪到 controller 的檢查,而 Tomcat 對
 * 非表單本文沒有預設上限——沒有這道 filter,一次請求就能把堆積吃光。
 */
@Tag("unit")
class RequestBodySizeLimitFilterTest {

    private static final String PATH = "/api/v1/iocs/import";
    private static final int MAX = 1024;

    private final RequestBodySizeLimitFilter filter =
            new RequestBodySizeLimitFilter(PATH, MAX, new FilterErrorWriter(clock()));

    private static ClockPort clock() {
        return () -> Instant.parse("2026-08-29T08:00:00Z");
    }

    private static MockHttpServletRequest request(byte[] body, boolean declareContentLength) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", PATH);
        request.setRequestURI(PATH);
        request.setContent(body);
        if (!declareContentLength) {
            // chunked:沒有 Content-Length,只能靠讀取時記帳擋下
            request.addHeader("Transfer-Encoding", "chunked");
            request.setContentType(null);
        }
        return request;
    }

    /** 宣告了 Content-Length 的:連讀都不必讀。 */
    @Test
    void oversizedDeclaredBodyIsRejectedBeforeReading() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request(new byte[MAX + 1], true), response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("PAYLOAD_TOO_LARGE");
        assertThat(chain.getRequest()).as("超限的請求不得往下走").isNull();
    }

    /**
     * 沒有 Content-Length 的(chunked)才是攻擊者會用的那一種:只檢查標頭等於沒擋。
     * 這裡讓下游真的去讀本文,驗證讀到上限的下一個位元組就中止。
     */
    @Test
    void oversizedChunkedBodyIsRejectedWhileReading() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = request(new byte[MAX + 1], false);
        MockFilterChain chain = readingChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("PAYLOAD_TOO_LARGE");
    }

    @Test
    void bodiesWithinTheLimitPassThroughIntact() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = readingChain();

        filter.doFilter(request(new byte[MAX], true), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    /** 別的路徑完全不受影響——這道上限只針對唯一以原始 byte 陣列收檔的端點。 */
    @Test
    void otherPathsAreNotLimited() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/iocs");
        request.setRequestURI("/api/v1/iocs");
        request.setContent(new byte[MAX * 4]);

        filter.doFilter(request, response, readingChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    /** 下游把本文讀完 —— 沒有這一步,chunked 的情形永遠不會觸發記帳。 */
    private static MockFilterChain readingChain() {
        return new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
                    throws IOException, jakarta.servlet.ServletException {
                try (InputStream body = request.getInputStream()) {
                    body.readAllBytes();
                }
                super.doFilter(request, response);
            }
        };
    }
}
