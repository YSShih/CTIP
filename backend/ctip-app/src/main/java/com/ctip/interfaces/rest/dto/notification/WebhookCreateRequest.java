package com.ctip.interfaces.rest.dto.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * 建立 webhook(docs/spec/09-api.md §9.1 的 {@code POST /webhooks})。
 *
 * <p>{@code targetUrl} 的 https 限制在此與聚合({@code Webhook.register})、
 * DB 約束({@code ck_wh_https})三處各驗一次:格式錯誤該回 400 而不是 500,
 * 而不變量 W1 的最終防線不能是 DTO 的標註。
 */
public record WebhookCreateRequest(
        @NotBlank @Size(max = 128) String name,

        @NotBlank @Size(max = 2048) @Pattern(regexp = "^https://.+", message = "targetUrl 必須為 https://")
        String targetUrl,

        @NotEmpty List<String> eventTypes,
        List<String> filterIocTypes,
        String filterMinSeverity,
        List<String> filterTags,
        List<UUID> filterSourceIds) {}
