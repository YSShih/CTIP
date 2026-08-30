package com.ctip;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ctip.infrastructure.observability.TraceIdFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * §9.4 的統一錯誤結構在 <strong>filter 層</strong>也必須成立(ADR 0015)。
 *
 * <p>filter 在 MVC 之前執行,{@code @RestControllerAdvice} 接不到;沒有最外層的錯誤網時,
 * 逸出的例外會落到 Boot 預設的 {@code /error},回出沒有 {@code code} 與 {@code traceId} 的結構。
 */
@AutoConfigureMockMvc
@Import(FilterErrorContractTest.ExplodingFilterConfig.class)
class FilterErrorContractTest extends AbstractPostgresIntegrationTest {

    private static final String CLIENT_IP = "10.20.0.31";
    static final String BOOM_PATH = "/api/v1/iocs";
    static final String BOOM_HEADER = "X-Test-Explode";

    @Autowired
    private MockMvc mvc;

    @Test
    void exceptionEscapingTheFilterChainStillYieldsTheUnifiedStructure() throws Exception {
        mvc.perform(asClient(get(BOOM_PATH).header(BOOM_HEADER, "yes")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.path").value(BOOM_PATH))
                .andExpect(jsonPath("$.message").value("Internal error"));
    }

    /** 對照組:沒有那個標頭時一切照常,錯誤網不影響正常路徑。 */
    @Test
    void normalRequestsAreUnaffected() throws Exception {
        mvc.perform(asClient(get(BOOM_PATH + "?limit=1"))).andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder asClient(MockHttpServletRequestBuilder builder) {
        return builder.with(request -> {
            request.setRemoteAddr(CLIENT_IP);
            return request;
        });
    }

    @TestConfiguration
    static class ExplodingFilterConfig {

        /** 排在 TraceIdFilter 之後,模擬「下游 filter 丟出未預期例外」。 */
        @Bean
        FilterRegistrationBean<Filter> explodingFilter() {
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(new ExplodingFilter());
            registration.setOrder(TraceIdFilter.ORDER + 2);
            return registration;
        }
    }

    private static final class ExplodingFilter implements Filter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            if (((HttpServletRequest) request).getHeader(BOOM_HEADER) != null) {
                throw new IllegalStateException("filter 內的未預期錯誤(測試用)");
            }
            chain.doFilter(request, response);
        }
    }
}
