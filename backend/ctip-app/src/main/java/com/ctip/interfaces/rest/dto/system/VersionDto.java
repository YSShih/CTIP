package com.ctip.interfaces.rest.dto.system;

/** GET /version(§9.1)。version 為建置版本(開發模式無 manifest 時為 dev)。 */
public record VersionDto(String apiVersion, String version) {}
