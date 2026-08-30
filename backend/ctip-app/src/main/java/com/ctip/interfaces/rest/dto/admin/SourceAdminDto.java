package com.ctip.interfaces.rest.dto.admin;

import java.util.UUID;

/** 調整後的來源狀態。 */
public record SourceAdminDto(UUID id, boolean enabled, String status, String lastErrorMessage) {}
