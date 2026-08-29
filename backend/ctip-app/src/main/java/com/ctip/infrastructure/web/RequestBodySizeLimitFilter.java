package com.ctip.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 請求本文的<strong>硬上限</strong>,在資料進到堆積之前就生效。
 *
 * <p>{@code POST /api/v1/iocs/import} 以 {@code @RequestBody byte[]} 收檔:Spring 會先把整包
 * 讀進一個 byte 陣列,controller 的 64MB 檢查<strong>在那之後</strong>才跑。Tomcat 對
 * <em>非表單</em>的請求本文沒有任何預設上限({@code max-http-form-post-size} 只管
 * {@code application/x-www-form-urlencoded}),因此一個持 {@code ioc:import} 的帳號送一份
 * 數 GB 的本文就能把 JVM 的堆積吃光——那是一次請求換一次 OOM。
 *
 * <p>兩種情形都要擋:宣告了 {@code Content-Length} 的,直接看標頭就回 413;
 * chunked(沒有 {@code Content-Length})的,則由 {@link BoundedServletInputStream} 在讀滿上限的
 * 下一個位元組時中止。後者才是攻擊者會用的那一種,只檢查標頭等於沒擋。
 */
public class RequestBodySizeLimitFilter extends OncePerRequestFilter {

    private final String pathPrefix;
    private final long maxBytes;
    private final FilterErrorWriter errorWriter;

    public RequestBodySizeLimitFilter(String pathPrefix, long maxBytes, FilterErrorWriter errorWriter) {
        this.pathPrefix = pathPrefix;
        this.maxBytes = maxBytes;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(pathPrefix);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            errorWriter.write(
                    request, response, 413, "PAYLOAD_TOO_LARGE", "Request body exceeds " + maxBytes + " bytes");
            return;
        }
        try {
            chain.doFilter(new BoundedRequest(request, maxBytes), response);
        } catch (BodyTooLargeException e) {
            if (!response.isCommitted()) {
                errorWriter.write(
                        request, response, 413, "PAYLOAD_TOO_LARGE", "Request body exceeds " + maxBytes + " bytes");
            }
        }
    }

    /** 讀超過上限時丟出;{@link IOException} 的子類,才不會被中間層當成程式錯誤包成 500。 */
    static final class BodyTooLargeException extends IOException {
        BodyTooLargeException(long maxBytes) {
            super("request body exceeds " + maxBytes + " bytes");
        }
    }

    private static final class BoundedRequest extends HttpServletRequestWrapper {

        private final long maxBytes;

        BoundedRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new BoundedServletInputStream(super.getInputStream(), maxBytes);
        }

        /** 也要包:否則任何改用 {@code getReader()} 的讀取路徑會直接繞過上限。 */
        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(
                    getInputStream(), encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding)));
        }
    }

    /** 逐位元組記帳的包裝;超過上限即中止,不再往下讀。 */
    private static final class BoundedServletInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maxBytes;
        private long read;

        BoundedServletInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0) {
                count(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = delegate.read(buffer, offset, length);
            if (count > 0) {
                count(count);
            }
            return count;
        }

        private void count(int increment) throws BodyTooLargeException {
            read += increment;
            if (read > maxBytes) {
                throw new BodyTooLargeException(maxBytes);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }

        @Override
        public int available() throws IOException {
            return delegate.available();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
