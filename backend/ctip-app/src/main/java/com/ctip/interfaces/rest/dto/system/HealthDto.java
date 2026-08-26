package com.ctip.interfaces.rest.dto.system;

/** GET /health(§9.1):liveness 語意;依賴健康(DB 等)見 /actuator/health。 */
public record HealthDto(String status) {}
