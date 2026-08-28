package com.ctip.config;

import java.util.Arrays;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS(05 §5.7:CORS_ALLOWED_ORIGINS → ctip.cors.allowed-origins;逗號分隔多來源)。
 * 只開放 /api/**;憑證走 Authorization / X-API-Key 標頭而非 cookie,故不開 allowCredentials。
 * DELETE 自 Phase 13 起需要(撤銷 API key)。Spring Security filter chain 以
 * {@code http.cors(...)} 沿用本設定(無 CorsConfigurationSource bean 時回退到 MVC 的對應)。
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
                .allowedMethods("GET", "POST", "DELETE")
                .allowedHeaders("*")
                // X-Bloom-*:Browser Extension 這類瀏覽器 client 必須讀得到下載回應的版本與 checksum,
                // 否則它只能相信 manifest 的版號,而那是「delta 可到達的最新版」而非這份 artifact 的版本
                .exposedHeaders(
                        "X-RateLimit-Limit",
                        "X-RateLimit-Remaining",
                        "X-RateLimit-Reset",
                        "Retry-After",
                        "X-Bloom-Scope",
                        "X-Bloom-Dataset-Version",
                        "X-Bloom-Version",
                        "X-Bloom-Checksum",
                        "X-Bloom-Compression",
                        "X-Bloom-Bit-Size",
                        "X-Bloom-Hash-Count")
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
