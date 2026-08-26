package com.ctip.infrastructure.persistence;

import com.ctip.application.port.StixObjectPort;
import com.ctip.domain.stix.StixProjection;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * stix_objects 的持久化 adapter(docs/spec/04-data-dictionary.md 表 8):
 * 以 stix_id UPSERT;content 於此序列化為 JSON(core 不碰 JSON;Boot 4 為 Jackson 3,
 * 套件 tools.jackson、序列化例外為 unchecked)。寫出於批次交易提交後執行,故自帶交易。
 */
@Component
class StixObjectAdapter implements StixObjectPort {

    private final StixObjectJpaRepository repository;
    private final ObjectMapper objectMapper;

    StixObjectAdapter(StixObjectJpaRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> findCreated(String stixId) {
        return repository.findByStixId(stixId).map(e -> e.stixCreated);
    }

    @Override
    @Transactional
    public void upsert(StixProjection projection) {
        StixObjectEntity entity = repository.findByStixId(projection.stixId()).orElseGet(StixObjectEntity::new);
        if (entity.id == null) {
            entity.id = UUID.randomUUID();
            entity.stixId = projection.stixId();
        }
        entity.stixType = projection.stixType();
        entity.specVersion = "2.1";
        entity.ownerTenantId = projection.ownerTenantId().value();
        entity.indicatorId = projection.indicatorId() == null
                ? null
                : projection.indicatorId().value();
        entity.tlp = projection.tlp().name();
        entity.stixCreated = projection.created();
        entity.stixModified = projection.modified();
        entity.content = objectMapper.writeValueAsString(projection.content());
        repository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findContent(String stixId) {
        return repository.findByStixId(stixId).map(e -> e.content);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> findContents(Collection<String> stixIds) {
        if (stixIds.isEmpty()) {
            return Map.of();
        }
        return repository.findByStixIdIn(stixIds).stream().collect(Collectors.toMap(e -> e.stixId, e -> e.content));
    }
}
