package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 統一錯誤回應(docs/spec/09-api.md §9.4):七個固定欄位、traceId 與日誌可對應、
 * 絕不洩漏 stack trace;跨端點一致。
 */
@AutoConfigureMockMvc
class ErrorResponseTest extends AbstractPostgresIntegrationTest {

    private static final String CLIENT_IP = "203.0.113.103";
    private static final List<String> FIELDS =
            List.of("timestamp", "status", "code", "message", "path", "traceId", "details");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void notFoundHasUnifiedShapeWithTraceId() throws Exception {
        MvcResult result = mvc.perform(request(get("/api/v1/iocs/00000000-0000-0000-0000-00000000dead")))
                .andExpect(status().isNotFound())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());

        List<String> keys = new ArrayList<>();
        body.propertyNames().forEach(keys::add);
        assertThat(keys).containsExactlyInAnyOrderElementsOf(FIELDS); // 無 stackTrace/exception 等欄位
        assertThat(body.get("status").asInt()).isEqualTo(404);
        assertThat(body.get("code").asString()).isEqualTo("NOT_FOUND");
        assertThat(body.get("path").asString()).isEqualTo("/api/v1/iocs/00000000-0000-0000-0000-00000000dead");
        assertThat(body.get("traceId").asString()).matches("[0-9a-f]{32}"); // 與日誌 MDC 對應
        assertThat(body.get("details").isArray()).isTrue();
    }

    @Test
    void incomingTraceparentIsHonored() throws Exception {
        mvc.perform(request(get("/api/v1/iocs/00000000-0000-0000-0000-00000000dead"))
                        .header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.traceId").value("4bf92f3577b34da6a3ce929d0e0e4736"));
    }

    @Test
    void invalidUuidPathIsInvalidRequestNotServerError() throws Exception {
        mvc.perform(request(get("/api/v1/iocs/not-a-uuid")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void validationFailureCarriesFieldDetails() throws Exception {
        mvc.perform(request(post("/api/v1/iocs/search"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.details[0].field").value("query"));
        mvc.perform(request(post("/api/v1/iocs/search"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"x\",\"type\":\"NOT_A_TYPE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.details[0].field").value("type"));
    }

    @Test
    void unsupportedMediaTypeAndMethodAreMapped() throws Exception {
        mvc.perform(request(post("/api/v1/iocs/search"))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("query=x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(request(delete("/api/v1/iocs")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void unknownRouteYieldsUnifiedNotFound() throws Exception {
        mvc.perform(request(get("/api/v1/no-such-resource")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void malformedJsonBodyIsInvalidRequest() throws Exception {
        mvc.perform(request(post("/api/v1/iocs/lookup"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private static MockHttpServletRequestBuilder request(MockHttpServletRequestBuilder builder) {
        return builder.with(req -> {
            req.setRemoteAddr(CLIENT_IP);
            return req;
        });
    }
}
