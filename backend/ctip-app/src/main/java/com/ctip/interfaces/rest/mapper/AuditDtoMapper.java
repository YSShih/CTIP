package com.ctip.interfaces.rest.mapper;

import com.ctip.application.audit.AuditRecord;
import com.ctip.interfaces.rest.dto.audit.AuditLogDto;
import org.mapstruct.Mapper;

/** 稽核軌跡 → DTO。{@code tenantId} 不輸出:查詢範圍本來就固定是呼叫者自己的租戶。 */
@Mapper(componentModel = "spring")
public interface AuditDtoMapper {

    default AuditLogDto toDto(AuditRecord record) {
        return new AuditLogDto(
                record.id(),
                record.occurredAt(),
                record.actorType().name(),
                record.actorId(),
                record.action().name(),
                record.resourceType(),
                record.resourceId(),
                record.ip(),
                record.userAgent(),
                record.result().name(),
                record.traceId(),
                record.metadata());
    }
}
