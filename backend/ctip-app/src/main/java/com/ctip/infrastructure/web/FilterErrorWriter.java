package com.ctip.infrastructure.web;

import com.ctip.application.port.ClockPort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.http.MediaType;

/**
 * Servlet filter 內的統一錯誤輸出(docs/spec/09-api.md §9.4 的結構)。
 *
 * <p>filter 在 MVC 之前執行,{@code @RestControllerAdvice} 接不到,因此手工組 JSON;
 * 且 interfaces 層依賴 infrastructure(TenantContext),反向依賴會使 ArchUnit 規則 5 成環,
 * 故不能重用 {@code ErrorResponse} record。
 */
public final class FilterErrorWriter {

    private final ClockPort clock;

    public FilterErrorWriter(ClockPort clock) {
        this.clock = clock;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter()
                .write("{\"timestamp\":\"" + clock.now() + "\",\"status\":" + status + ",\"code\":\"" + code
                        + "\",\"message\":\"" + escapeJson(message) + "\",\"path\":\""
                        + escapeJson(request.getRequestURI()) + "\",\"traceId\":"
                        + jsonStringOrNull(MDC.get("traceId")) + ",\"details\":[]}");
    }

    private static String jsonStringOrNull(String value) {
        return value == null ? "null" : "\"" + escapeJson(value) + "\"";
    }

    /** 最小 JSON 字串跳脫(引號、反斜線、控制字元)——不押注 servlet 容器對 request line 的過濾。 */
    public static String escapeJson(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
