package com.ctip.interfaces.rest;

import com.ctip.domain.shared.Cursor;
import com.ctip.interfaces.rest.error.ApiException;
import com.ctip.interfaces.rest.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 對外 cursor 編解碼(docs/spec/09-api.md §9.3):base64url(JSON of {"ls","id"}),對外不透明。
 * domain 的 {@link Cursor#encode()} 是內部格式;對外一律經本類包裝,編解碼集中於此。
 * 無法解析 → 400 INVALID_CURSOR。
 */
@Component
public class CursorCodec {

    /** 線上格式:ls = lastSeen(ISO-8601)、id = uuid(§9.3 範例)。 */
    record Payload(String ls, String id) {}

    private final ObjectMapper objectMapper;

    CursorCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 包裝 repository 回傳的內部 nextCursor;null 透傳(最後一頁)。 */
    public String wrapInternal(String internalCursor) {
        if (internalCursor == null) {
            return null;
        }
        return encode(Cursor.decode(internalCursor));
    }

    public String encode(Cursor cursor) {
        String json = objectMapper.writeValueAsString(
                new Payload(cursor.lastSeen().toString(), cursor.id().toString()));
        return Base64.getUrlEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /** null/空白視為第一頁;其餘必須可解析,否則 INVALID_CURSOR。 */
    public Cursor decode(String externalCursor) {
        if (externalCursor == null || externalCursor.isBlank()) {
            return null;
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(externalCursor);
            Payload payload = objectMapper.readValue(new String(json, StandardCharsets.UTF_8), Payload.class);
            return new Cursor(Instant.parse(payload.ls()), UUID.fromString(payload.id()));
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.INVALID_CURSOR, "Cursor cannot be parsed");
        }
    }
}
