package com.ctip.interfaces.rest.dto.notification;

import java.util.List;
import java.util.UUID;

/** 伺服器端訂閱過濾條件(不變量 W5)。各維度空陣列 = 不限;{@code minSeverity} 為 null = 不限。 */
public record WebhookFilterDto(List<String> iocTypes, String minSeverity, List<String> tags, List<UUID> sourceIds) {}
