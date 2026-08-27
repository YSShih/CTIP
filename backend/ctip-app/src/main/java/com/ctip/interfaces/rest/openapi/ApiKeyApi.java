package com.ctip.interfaces.rest.openapi;

import com.ctip.interfaces.rest.dto.apikey.ApiKeyCreateRequest;
import com.ctip.interfaces.rest.dto.apikey.ApiKeyDto;
import com.ctip.interfaces.rest.dto.apikey.IssuedApiKeyDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

/** API key 端點的 OpenAPI 文件(§9.1、§10.5);controller 實作本介面以繼承註解。 */
@Tag(name = "API Keys", description = "Machine-to-machine credentials scoped to the caller's tenant.")
public interface ApiKeyApi {

    String KEY_EXAMPLE = """
            {"id":"9c7a1e42-5f3b-4a10-9d2c-7e8f0a1b2c3d","name":"ci-pipeline","keyPrefix":"aB3xY9kQ",\
            "scopes":["ioc:read"],"expiresAt":null,"lastUsedAt":null,"revokedAt":null,\
            "createdAt":"2026-08-27T07:00:00Z"}""";

    @Operation(
            summary = "List the tenant's API keys",
            description = "Returns every key issued for the caller's tenant, including revoked ones. The key "
                    + "material itself is never returned. 認證:需要 Bearer JWT 或 X-API-Key,權限 apikey:create。")
    @ApiResponse(
            responseCode = "200",
            description = "API keys of the caller's tenant",
            content =
                    @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ApiKeyDto.class)),
                            examples = @ExampleObject(value = "[" + KEY_EXAMPLE + "]")))
    @ApiResponse(responseCode = "403", description = "Missing apikey:create permission (FORBIDDEN)")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    List<ApiKeyDto> listApiKeys();

    @Operation(
            summary = "Create an API key",
            description = "Issues a key for the caller's tenant. The full key is returned exactly once and only "
                    + "its SHA-256 hash is stored; scopes may not exceed the creator's own permissions. "
                    + "認證:需要 Bearer JWT 或 X-API-Key,權限 apikey:create。")
    @ApiResponse(
            responseCode = "201",
            description = "Key created; full key returned once",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = IssuedApiKeyDto.class),
                            examples =
                                    @ExampleObject(
                                            value = "{\"key\":\"ctip_mvp_aB3xY9kQ7fLm2pR8sT4uV6wX0yZ1cD5e\","
                                                    + "\"apiKey\":" + KEY_EXAMPLE + "}")))
    @ApiResponse(responseCode = "400", description = "Scope exceeds creator permissions (INVALID_REQUEST)")
    @ApiResponse(responseCode = "403", description = "Missing apikey:create permission (FORBIDDEN)")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    ResponseEntity<IssuedApiKeyDto> create(ApiKeyCreateRequest request);

    @Operation(
            summary = "Revoke an API key",
            description = "Revocation is irreversible. Keys belonging to another tenant are reported as not "
                    + "found rather than forbidden. 認證:需要 Bearer JWT 或 X-API-Key,權限 apikey:revoke。")
    @ApiResponse(
            responseCode = "204",
            description = "Key revoked",
            content =
                    @Content(mediaType = "application/json", examples = @ExampleObject(name = "empty", value = "null")))
    @ApiResponse(responseCode = "403", description = "Missing apikey:revoke permission (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Unknown key or another tenant's key (NOT_FOUND)")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    ResponseEntity<Void> revoke(UUID id);
}
