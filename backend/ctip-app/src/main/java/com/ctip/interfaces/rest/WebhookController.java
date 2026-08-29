package com.ctip.interfaces.rest;

import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.application.notification.NewWebhookCommand;
import com.ctip.application.notification.WebhookManagementService;
import com.ctip.domain.notification.NotificationType;
import com.ctip.domain.notification.WebhookFilter;
import com.ctip.domain.notification.WebhookId;
import com.ctip.infrastructure.security.TenantContext;
import com.ctip.interfaces.rest.dto.notification.IssuedWebhookDto;
import com.ctip.interfaces.rest.dto.notification.WebhookCreateRequest;
import com.ctip.interfaces.rest.dto.notification.WebhookDto;
import com.ctip.interfaces.rest.error.ApiException;
import com.ctip.interfaces.rest.error.ErrorCode;
import com.ctip.interfaces.rest.mapper.NotificationDtoMapper;
import com.ctip.interfaces.rest.openapi.WebhookApi;
import com.ctip.sdk.IocType;
import com.ctip.sdk.Severity;
import jakarta.validation.Valid;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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
 * Webhook 管理端點(docs/spec/09-api.md §9.1「通知與稽核」,權限 {@code webhook:manage})。
 * 租戶範圍取自 {@link TenantContext};別的租戶的 webhook 一律 404,不回 403(不洩漏存在性)。
 */
@RestController
@RequestMapping("/api/v1/webhooks")
class WebhookController implements WebhookApi {

    private final WebhookManagementService webhooks;
    private final TenantContext tenantContext;
    private final NotificationDtoMapper mapper;

    WebhookController(WebhookManagementService webhooks, TenantContext tenantContext, NotificationDtoMapper mapper) {
        this.webhooks = webhooks;
        this.tenantContext = tenantContext;
        this.mapper = mapper;
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('webhook:manage')")
    public List<WebhookDto> listWebhooks() {
        return webhooks.list(tenantContext.tenantId()).stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('webhook:manage')")
    public ResponseEntity<IssuedWebhookDto> createWebhook(@Valid @RequestBody WebhookCreateRequest request) {
        AuthenticatedIdentity creator = tenantContext.requireIdentity();
        WebhookManagementService.IssuedWebhook issued = webhooks.register(new NewWebhookCommand(
                creator.tenantId(),
                creator.userId(),
                request.name(),
                request.targetUrl(),
                parseSet(request.eventTypes(), NotificationType::valueOf, "eventTypes"),
                new WebhookFilter(
                        parseSet(request.filterIocTypes(), IocType::valueOf, "filterIocTypes"),
                        parseSeverity(request.filterMinSeverity()),
                        request.filterTags() == null ? Set.of() : Set.copyOf(request.filterTags()),
                        request.filterSourceIds() == null ? Set.of() : Set.copyOf(request.filterSourceIds()))));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new IssuedWebhookDto(issued.secret(), mapper.toDto(issued.webhook())));
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('webhook:manage')")
    public ResponseEntity<Void> deleteWebhook(@PathVariable UUID id) {
        if (!webhooks.delete(new WebhookId(id), tenantContext.tenantId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "No such webhook");
        }
        return ResponseEntity.noContent().build();
    }

    /** 未知的列舉值是 client 的輸入錯誤(400),不是伺服器錯誤——{@code valueOf} 直接丟會變成 500。 */
    private static <T> Set<T> parseSet(List<String> values, Function<String, T> parser, String field) {
        if (values == null) {
            return Set.of();
        }
        try {
            return values.stream().map(parser).collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, field + " 含未知的值");
        }
    }

    private static Severity parseSeverity(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Severity.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "filterMinSeverity 含未知的值");
        }
    }
}
