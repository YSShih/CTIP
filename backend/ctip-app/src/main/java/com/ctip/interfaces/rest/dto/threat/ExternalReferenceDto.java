package com.ctip.interfaces.rest.dto.threat;

/** Threat 的外部參照(04 表 21):externalId 與 url 至少有一個非 null(不變量 H3)。 */
public record ExternalReferenceDto(String sourceName, String externalId, String url, String description) {}
