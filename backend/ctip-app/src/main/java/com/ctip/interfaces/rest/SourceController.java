package com.ctip.interfaces.rest;

import com.ctip.application.source.SourceQueryService;
import com.ctip.domain.source.Source;
import com.ctip.domain.source.SourceId;
import com.ctip.interfaces.rest.dto.source.SourceDto;
import com.ctip.interfaces.rest.dto.source.SourceStatusDto;
import com.ctip.interfaces.rest.error.ApiException;
import com.ctip.interfaces.rest.mapper.SourceDtoMapper;
import com.ctip.interfaces.rest.openapi.SourceApi;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 來源端點(docs/spec/09-api.md §9.1,匿名)。sources 表無租戶歸屬,不需 tenant 過濾。 */
@RestController
@RequestMapping("/api/v1/sources")
class SourceController implements SourceApi {

    private final SourceQueryService sources;
    private final SourceDtoMapper mapper;

    SourceController(SourceQueryService sources, SourceDtoMapper mapper) {
        this.sources = sources;
        this.mapper = mapper;
    }

    @Override
    @GetMapping
    public List<SourceDto> list() {
        return sources.all().stream().map(mapper::toDto).toList();
    }

    @Override
    @GetMapping("/{id}")
    public SourceDto byId(@PathVariable UUID id) {
        return mapper.toDto(source(id));
    }

    @Override
    @GetMapping("/{id}/status")
    public SourceStatusDto status(@PathVariable UUID id) {
        return mapper.toStatusDto(source(id));
    }

    private Source source(UUID id) {
        return sources.byId(new SourceId(id)).orElseThrow(ApiException::notFound);
    }
}
