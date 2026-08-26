package com.ctip.interfaces.rest.dto.ioc;

/** 來源標註(07 §7.9 規則 4:顯示名稱與 homepage;homepage 來源未提供時為 null)。 */
public record AttributionDto(String sourceName, String homepage) {}
