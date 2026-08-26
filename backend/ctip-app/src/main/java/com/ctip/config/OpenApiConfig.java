package com.ctip.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger(docs/spec/09-api.md §9.6):springdoc 3.1.0(3.x 才相容 Boot 4)。
 * 開關由 SWAGGER_ENABLED 控制(application.yml;prod 樣板預設 false)。
 * 全端點共通的錯誤回應(429 限流、500 內部錯誤)由 OperationCustomizer 統一掛上,
 * 個別端點的 400/404/413 等在各 *Api 文件介面標註。
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI ctipOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CTIP API")
                        .version("v1")
                        .description("Cyber Threat Intelligence Platform read API. M1 exposes anonymous "
                                + "read access to public TLP:CLEAR intelligence with cursor pagination; "
                                + "authentication, RBAC and write endpoints arrive in M2. "
                                + "All errors share a single ErrorResponse shape with a log-correlated "
                                + "traceId (see docs/spec/09-api.md §9.4)."));
    }

    /** 429(所有端點皆受匿名限流)與 500 的統一錯誤文件;個別端點不必重複標註。 */
    @Bean
    OperationCustomizer globalErrorResponses() {
        return (operation, handlerMethod) -> {
            operation.getResponses().addApiResponse("429", errorResponse("Rate limit exceeded (RATE_LIMIT_EXCEEDED)"));
            operation.getResponses().addApiResponse("500", errorResponse("Unexpected error (INTERNAL_ERROR)"));
            return operation;
        };
    }

    private static ApiResponse errorResponse(String description) {
        Schema<?> ref = new Schema<>().$ref("#/components/schemas/ErrorResponse");
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json", new MediaType().schema(ref)));
    }
}
