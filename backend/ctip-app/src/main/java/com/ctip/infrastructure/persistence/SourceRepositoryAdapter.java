package com.ctip.infrastructure.persistence;

import com.ctip.application.port.SourceRepository;
import com.ctip.domain.source.Source;
import com.ctip.domain.source.SourceId;
import com.ctip.sdk.SourceType;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** SourceRepository port 的 JPA 實作。來源列由種子/管理流程建立,save 僅更新既有列。 */
@Repository
@Transactional
class SourceRepositoryAdapter implements SourceRepository {

    private final SourceJpaRepository jpa;
    private final SourceMapper mapper;

    SourceRepositoryAdapter(SourceJpaRepository jpa, SourceMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Source> findById(SourceId id) {
        return jpa.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Source> findBySourceType(SourceType sourceType) {
        return jpa.findBySourceType(sourceType.name()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Source> findEnabledSyncable() {
        return jpa.findByEnabledTrueAndSyncableTrue().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Source> findAll() {
        return jpa.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public Source save(Source source) {
        SourceEntity entity = jpa.findById(source.id().value())
                .orElseThrow(() -> new IllegalStateException("來源不存在,無法更新:" + source.id()));
        mapper.updateEntity(source, entity);
        return mapper.toDomain(jpa.save(entity));
    }
}
