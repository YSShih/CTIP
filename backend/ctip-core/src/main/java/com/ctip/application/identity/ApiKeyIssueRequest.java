package com.ctip.application.identity;

import com.ctip.domain.identity.ScopeSet;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.UserId;
import java.time.Instant;

/** 建立 API key 的輸入。{@code expiresAt} 為 null 表示不過期(不變量 K7)。 */
public record ApiKeyIssueRequest(TenantId tenantId, UserId userId, String name, ScopeSet scopes, Instant expiresAt) {}
