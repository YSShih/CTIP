package com.ctip.interfaces.rest.dto.threat;

import com.ctip.domain.threat.IndicatorRole;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 建立／更新 Threat 與 IOC 的關聯(04 表 20)。
 * {@code role} 省略時為 {@code UNKNOWN}(與 {@code threat_indicators.role} 的欄位預設一致)。
 */
public record ThreatLinkRequest(@Schema(example = "C2") IndicatorRole role) {}
