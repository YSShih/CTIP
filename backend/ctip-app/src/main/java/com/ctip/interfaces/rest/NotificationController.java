package com.ctip.interfaces.rest;

import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.application.notification.NotificationQuery;
import com.ctip.application.notification.NotificationRecord;
import com.ctip.application.notification.NotificationService;
import com.ctip.domain.shared.CursorPage;
import com.ctip.infrastructure.security.TenantContext;
import com.ctip.interfaces.rest.dto.common.PageResponse;
import com.ctip.interfaces.rest.dto.notification.NotificationDto;
import com.ctip.interfaces.rest.error.ApiException;
import com.ctip.interfaces.rest.error.ErrorCode;
import com.ctip.interfaces.rest.mapper.NotificationDtoMapper;
import com.ctip.interfaces.rest.openapi.NotificationApi;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站內通知端點(docs/spec/09-api.md §9.1「通知與稽核」)。
 * 可見範圍取自 {@link TenantContext},呼叫端不得指定——沒有「看別的租戶的通知」這件事。
 */
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController implements NotificationApi {

    private final NotificationService notifications;
    private final ReadQuotaPolicy readQuota;
    private final TenantContext tenantContext;
    private final NotificationDtoMapper mapper;
    private final CursorCodec cursors;

    NotificationController(
            NotificationService notifications,
            ReadQuotaPolicy readQuota,
            TenantContext tenantContext,
            NotificationDtoMapper mapper,
            CursorCodec cursors) {
        this.notifications = notifications;
        this.readQuota = readQuota;
        this.tenantContext = tenantContext;
        this.mapper = mapper;
        this.cursors = cursors;
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('notification:read')")
    public PageResponse<NotificationDto> listNotifications(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        AuthenticatedIdentity caller = tenantContext.requireIdentity();
        CursorPage<NotificationRecord> page = notifications.list(new NotificationQuery(
                caller.tenantId(),
                userIdOf(caller),
                unreadOnly,
                cursors.decode(cursor),
                readQuota.clampPageSize(caller.tenantId(), limit)));
        return new PageResponse<>(
                page.items().stream().map(mapper::toDto).toList(),
                cursors.wrapInternal(page.nextCursor()),
                page.hasMore());
    }

    @Override
    @PatchMapping("/{id}/read")
    @PreAuthorize("hasAuthority('notification:read')")
    public ResponseEntity<Void> markRead(@PathVariable UUID id) {
        AuthenticatedIdentity caller = tenantContext.requireIdentity();
        if (!notifications.markRead(id, caller.tenantId(), userIdOf(caller))) {
            // 不在可見範圍內與「已讀」都回 404:回 403 等於承認那個 id 存在
            throw new ApiException(ErrorCode.NOT_FOUND, "No unread notification with that id");
        }
        return ResponseEntity.noContent().build();
    }

    /** API key 身分沒有使用者;它只看得到廣播型通知。 */
    private static UUID userIdOf(AuthenticatedIdentity caller) {
        return caller.userId() == null ? null : caller.userId().value();
    }
}
