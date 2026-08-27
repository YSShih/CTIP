package com.ctip.interfaces.rest.mapper;

import com.ctip.domain.identity.ApiKey;
import com.ctip.interfaces.rest.dto.apikey.ApiKeyDto;
import org.mapstruct.Mapper;

/** ApiKey → DTO。原文與雜湊皆不外流,只暴露前綴(不變量 K1)。 */
@Mapper(componentModel = "spring")
public interface ApiKeyDtoMapper {

    default ApiKeyDto toDto(ApiKey apiKey) {
        return new ApiKeyDto(
                apiKey.id().value().toString(),
                apiKey.name(),
                apiKey.keyPrefix().value(),
                apiKey.scopes().values(),
                apiKey.expiresAt(),
                apiKey.lastUsedAt(),
                apiKey.revokedAt(),
                apiKey.createdAt());
    }
}
