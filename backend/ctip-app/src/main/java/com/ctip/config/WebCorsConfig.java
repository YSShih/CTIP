package com.ctip.config;

import java.util.Arrays;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS(05 §5.7:CORS_ALLOWED_ORIGINS → ctip.cors.allowed-origins;逗號分隔多來源)。
 * 只開放 /api/**;M1 全讀取 + 少量 POST 查詢端點,無憑證(cookie)需求故不開 allowCredentials。
 * StartupValidator 已擋 prod 萬用字元來源。
 */
@Configuration
class WebCorsConfig implements WebMvcConfigurer {

    private final CtipProperties.Cors cors;

    WebCorsConfig(CtipProperties properties) {
        this.cors = properties.cors();
    }

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(parseOrigins(cors.allowedOrigins()))
                .allowedMethods("GET", "POST")
                .allowedHeaders("*")
                .exposedHeaders("X-RateLimit-Limit", "X-RateLimit-Remaining", "X-RateLimit-Reset", "Retry-After")
                .maxAge(3600);
    }

    /** 逗號分隔清單;項目 trim 並濾除空字串——「a.com, b.com」的慣用寫法不得使第二項靜默失效。 */
    static String[] parseOrigins(String allowedOrigins) {
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }
}
