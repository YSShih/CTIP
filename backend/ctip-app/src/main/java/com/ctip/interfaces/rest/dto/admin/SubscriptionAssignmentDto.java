package com.ctip.interfaces.rest.dto.admin;

import java.time.Instant;
import java.util.UUID;

/** 指派方案後的訂閱狀態。 */
public record SubscriptionAssignmentDto(
        UUID subscriptionId, UUID tenantId, String planCode, String status, Instant cancelledAt) {}
