package com.ctip.interfaces.rest;

import com.ctip.application.identity.ApiKeyIssueRequest;
import com.ctip.application.identity.ApiKeyService;
import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.domain.identity.ApiKeyId;
import com.ctip.domain.identity.IssuedApiKey;
import com.ctip.domain.identity.ScopeSet;
import com.ctip.infrastructure.security.TenantContext;
import com.ctip.interfaces.rest.dto.apikey.ApiKeyCreateRequest;
import com.ctip.interfaces.rest.dto.apikey.ApiKeyDto;
import com.ctip.interfaces.rest.dto.apikey.IssuedApiKeyDto;
import com.ctip.interfaces.rest.mapper.ApiKeyDtoMapper;
import com.ctip.interfaces.rest.openapi.ApiKeyApi;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API key 端點(docs/spec/09-api.md §9.1、§10.5)。
 * 授權一律以 {@code @PreAuthorize} 表達,controller 內不得出現角色判斷(§10.3)。
 * 租戶範圍由 {@link TenantContext} 決定,不由呼叫端指定(§10.1 隔離規則)。
 */
@RestController
@RequestMapping("/api/v1/api-keys")
class ApiKeyController implements ApiKeyApi {

    private final ApiKeyService apiKeys;
    private final ApiKeyDtoMapper mapper;
    private final TenantContext tenantContext;

    ApiKeyController(ApiKeyService apiKeys, ApiKeyDtoMapper mapper, TenantContext tenantContext) {
        this.apiKeys = apiKeys;
        this.mapper = mapper;
        this.tenantContext = tenantContext;
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('apikey:create')")
    public List<ApiKeyDto> listApiKeys() {
        return apiKeys.list(tenantContext.tenantId()).stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('apikey:create')")
    public ResponseEntity<IssuedApiKeyDto> create(@Valid @RequestBody ApiKeyCreateRequest request) {
        AuthenticatedIdentity creator = tenantContext.requireIdentity();
        IssuedApiKey issued = apiKeys.issue(
                new ApiKeyIssueRequest(
                        creator.tenantId(),
                        creator.userId(),
                        request.name(),
                        new ScopeSet(request.scopes()),
                        request.expiresAt()),
                creator);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new IssuedApiKeyDto(issued.plaintext(), mapper.toDto(issued.apiKey())));
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('apikey:revoke')")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        apiKeys.revoke(new ApiKeyId(id), tenantContext.tenantId());
        return ResponseEntity.noContent().build();
    }
}
