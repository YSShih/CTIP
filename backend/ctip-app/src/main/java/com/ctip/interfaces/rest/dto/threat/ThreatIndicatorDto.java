package com.ctip.interfaces.rest.dto.threat;

import com.ctip.interfaces.rest.dto.ioc.IocDto;
import java.time.Instant;

/** {@code GET /threats/{id}/indicators} 的一列:關聯屬性 + 對 viewer 可見的 IOC。 */
public record ThreatIndicatorDto(String role, Instant addedAt, IocDto ioc) {}
