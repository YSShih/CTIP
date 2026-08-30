package com.ctip.infrastructure.observability;

import com.ctip.infrastructure.web.FilterErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code /actuator/prometheus} 的來源 IP 限制(docs/spec/13-platform-ops.md §13.6:
 * 「{@code prometheus} 需限制來源 IP」)。
 *
 * <p>指標端點會洩漏租戶數、來源清單、端點路徑與流量樣態,而 {@code SecurityConfig} 是
 * {@code anyRequest().permitAll()}(授權一律在方法層),actuator 端點沒有任何方法層宣告可掛——
 * 這道限制因此只能是一個 filter。白名單為空即拒絕所有來源。
 *
 * <p>IP 取自 {@code request.getRemoteAddr()},而它已由 {@code TrustedProxyForwardedHeaderFilter}
 * 依 {@code ctip.proxy.trusted} 還原(§10.7);未列為信任代理的來源無法自稱來自別的位址。
 */
public class PrometheusAccessFilter extends OncePerRequestFilter {

    static final String PATH = "/actuator/prometheus";

    private static final Logger log = LoggerFactory.getLogger(PrometheusAccessFilter.class);

    private final List<IpAddressMatcher> allowed;
    private final FilterErrorWriter errorWriter;

    public PrometheusAccessFilter(List<String> allowedCidrs, FilterErrorWriter errorWriter) {
        this.allowed = allowedCidrs.stream()
                .map(String::trim)
                .filter(cidr -> !cidr.isEmpty())
                .map(IpAddressMatcher::new)
                .toList();
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isAllowed(request.getRemoteAddr())) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("拒絕來自 {} 的 /actuator/prometheus 抓取(不在 PROMETHEUS_ALLOWED_IPS 內)", request.getRemoteAddr());
        errorWriter.write(request, response, 403, "FORBIDDEN", "Metrics scraping is not allowed from this address");
    }

    private boolean isAllowed(String remoteAddr) {
        if (remoteAddr == null) {
            return false;
        }
        return allowed.stream().anyMatch(matcher -> matcher.matches(remoteAddr));
    }
}
