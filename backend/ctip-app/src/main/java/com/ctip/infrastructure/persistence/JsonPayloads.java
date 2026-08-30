package com.ctip.infrastructure.persistence;

import java.util.Map;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code indicator_sources.raw_payload}(JSONB)的序列化。
 * Boot 4 的 Jackson 是 3.x,套件為 {@code tools.jackson..}(06 §6.3.6)。
 * ObjectMapper 為執行緒安全的不可變物件,故以常數共用。
 */
final class JsonPayloads {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private JsonPayloads() {}

    static String toJson(Map<String, Object> payload) {
        return MAPPER.writeValueAsString(payload);
    }

    /** 讀回 JSONB 欄位;內容由本平台寫入,結構固定為物件。 */
    @SuppressWarnings("unchecked")
    static Map<String, Object> toMap(String json) {
        return json == null || json.isBlank() ? Map.of() : MAPPER.readValue(json, Map.class);
    }
}
