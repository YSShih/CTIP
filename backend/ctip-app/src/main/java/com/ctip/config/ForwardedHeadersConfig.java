package com.ctip.config;

import com.ctip.infrastructure.web.TrustedProxies;
import com.ctip.infrastructure.web.TrustedProxyForwardedHeaderFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * 反向代理下的 client IP(docs/spec/10-identity-plans.md §10.7)。
 *
 * <p>{@code server.forward-headers-strategy=framework} 已在 {@code application.yml} 設定;
 * 這裡提供帶「信任來源」限制的 {@link ForwardedHeaderFilter} 取代 Boot 的無條件版本
 * (Boot 的那個 bean 標了 {@code @ConditionalOnMissingFilterBean},故此 bean 存在時它退讓)。
 * 順序與 Boot 相同:{@link Ordered#HIGHEST_PRECEDENCE}——所有其他 filter(含限流)
 * 都必須看到已修正的 {@code getRemoteAddr()}。
 */
@Configuration(proxyBeanMethods = false)
public class ForwardedHeadersConfig {

    @Bean
    TrustedProxies trustedProxies(CtipProperties properties) {
        return new TrustedProxies(properties.proxy().trusted());
    }

    @Bean
    FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter(TrustedProxies trustedProxies) {
        FilterRegistrationBean<ForwardedHeaderFilter> registration =
                new FilterRegistrationBean<>(new TrustedProxyForwardedHeaderFilter(trustedProxies));
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
