package com.ctip.interfaces.rest.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;

/** 登入身分摘要。與 JWT claims 一致,不含 email 以外的個資;displayName 由前端顯示用。 */
public record SessionUserDto(
        @Schema(example = "3f1b0c2e-9a4d-4c1a-8b77-2b0f1a9c5d10")
        String userId,

        @Schema(example = "8b1a9c33-2f4e-4d55-9f6a-0c1d2e3f4a5b")
        String tenantId,

        @Schema(example = "TENANT_ADMIN") String role,
        @Schema(example = "[\"ioc:read\",\"ioc:submit\"]") Set<String> permissions,
        @Schema(example = "Alice Analyst") String displayName) {}
