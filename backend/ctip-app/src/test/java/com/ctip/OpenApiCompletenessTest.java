package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * OpenAPI 完整性(docs/spec/09-api.md §9.6):解析 /v3/api-docs,逐端點檢查
 * summary、description、response schema、錯誤回應、認證需求(描述含「認證」)、至少一個範例;
 * POST 端點另檢查 request schema。並將產出寫入 docs/api/openapi.json
 * (鍵排序 + pretty print,確保可重現;§9.6:CI 比對 committed 版本,不得手改)。
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenApiCompletenessTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode apiDocs;
    private String rawJson;

    @BeforeAll
    void fetchApiDocs() throws Exception {
        rawJson = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        apiDocs = objectMapper.readTree(rawJson);
    }

    @Test
    void everyOperationHasSummaryDescriptionAuthErrorsAndExample() {
        JsonNode paths = apiDocs.get("paths");
        assertThat(paths.size()).isGreaterThanOrEqualTo(14); // §9.1 的 M1 端點數(15 個 operation)
        for (Map.Entry<String, JsonNode> path : paths.properties()) {
            for (Map.Entry<String, JsonNode> method : path.getValue().properties()) {
                String where = method.getKey().toUpperCase(java.util.Locale.ROOT) + " " + path.getKey();
                JsonNode op = method.getValue();
                assertThat(op.path("summary").asString(""))
                        .as("%s summary", where)
                        .isNotBlank();
                assertThat(op.path("description").asString(""))
                        .as("%s description", where)
                        .isNotBlank()
                        .contains("認證"); // 認證需求必須明載
                assertOkResponseWithSchema(op, where);
                assertErrorResponse(op, where);
                assertThat(containsExample(op)).as("%s 至少一個範例", where).isTrue();
                if ("post".equals(method.getKey())) {
                    assertThat(op.at("/requestBody/content/application~1json/schema")
                                    .isMissingNode())
                            .as("%s request schema", where)
                            .isFalse();
                }
            }
        }
    }

    /**
     * §9.6 要求每個端點記載回應內容,<strong>但不限 JSON</strong>:
     * {@code GET /sync/bloom} 回的是 {@code application/octet-stream} 的位元陣列(11 §11.5),
     * 把檢查寫死在 {@code application/json} 會逼它假裝自己回 JSON。
     */
    private static void assertOkResponseWithSchema(JsonNode op, String where) {
        JsonNode responses = op.get("responses");
        boolean documented = false;
        for (Map.Entry<String, JsonNode> r : responses.properties()) {
            if (r.getKey().startsWith("2")
                    && !r.getValue().path("content").properties().isEmpty()) {
                documented = true;
            }
        }
        assertThat(documented).as("%s 2xx response content", where).isTrue();
    }

    private static void assertErrorResponse(JsonNode op, String where) {
        boolean hasError = op.get("responses").propertyNames().stream()
                .anyMatch(code -> code.startsWith("4") || code.startsWith("5"));
        assertThat(hasError).as("%s 錯誤回應", where).isTrue();
    }

    /** operation 子樹內任一處出現 example/examples 即符合「至少一個範例」。 */
    private static boolean containsExample(JsonNode node) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> property : node.properties()) {
                if (property.getKey().equals("example") || property.getKey().equals("examples")) {
                    return true;
                }
                if (containsExample(property.getValue())) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                if (containsExample(element)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void errorResponseSchemaAndInfoArePresent() {
        assertThat(apiDocs.at("/components/schemas/ErrorResponse").isMissingNode())
                .isFalse();
        assertThat(apiDocs.at("/info/title").asString()).isEqualTo("CTIP API");
        assertThat(apiDocs.at("/info/version").asString()).isEqualTo("v1");
    }

    /** 產出 docs/api/openapi.json(§9.6:由建置產生並 commit;CI 比對 drift 與破壞性變更)。 */
    @Test
    void writesCanonicalOpenApiJsonForCommit() throws Exception {
        ObjectMapper canonical = JsonMapper.builder()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();
        Object tree = canonical.readValue(rawJson, Object.class);
        Path target = Path.of("")
                .toAbsolutePath()
                .resolve("../../docs/api/openapi.json")
                .normalize();
        Files.createDirectories(target.getParent());
        Files.writeString(target, canonical.writeValueAsString(tree) + "\n");
        assertThat(Files.size(target)).isGreaterThan(1000);
    }
}
