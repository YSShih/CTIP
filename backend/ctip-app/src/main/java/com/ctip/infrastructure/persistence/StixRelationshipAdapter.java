package com.ctip.infrastructure.persistence;

import com.ctip.application.port.StixRelationshipPort;
import com.ctip.domain.stix.StixRelationship;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.Tlp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * stix_relationships 的持久化 adapter(docs/spec/04-data-dictionary.md 表 9)。
 * 表 9 沒有 content 欄:只落三元組與信封欄位,對外 JSON 由投影規則於讀取時重建。
 */
@Component
class StixRelationshipAdapter implements StixRelationshipPort {

    private final StixRelationshipJpaRepository repository;

    StixRelationshipAdapter(StixRelationshipJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void syncForTarget(String targetRef, List<StixRelationship> relationships) {
        Map<String, StixRelationshipEntity> existing = repository.findByTargetRef(targetRef).stream()
                .collect(Collectors.toMap(entity -> entity.stixId, Function.identity()));
        Map<String, StixRelationship> desired =
                relationships.stream().collect(Collectors.toMap(StixRelationship::stixId, Function.identity()));
        List<StixRelationshipEntity> removed = existing.values().stream()
                .filter(entity -> !desired.containsKey(entity.stixId))
                .toList();
        repository.deleteAll(removed);
        for (StixRelationship relationship : desired.values()) {
            StixRelationshipEntity entity = existing.get(relationship.stixId());
            if (entity == null) {
                entity = new StixRelationshipEntity();
                entity.id = UUID.randomUUID();
                entity.stixId = relationship.stixId();
                entity.stixCreated = relationship.created();
            }
            entity.relationshipType = relationship.relationshipType();
            entity.sourceRef = relationship.sourceRef();
            entity.targetRef = relationship.targetRef();
            entity.ownerTenantId = relationship.ownerTenantId().value();
            entity.tlp = relationship.tlp().name();
            entity.stixModified = relationship.modified();
            repository.save(entity);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StixRelationship> findByStixId(String stixId) {
        return repository.findByStixId(stixId).map(StixRelationshipAdapter::toDomain);
    }

    private static StixRelationship toDomain(StixRelationshipEntity entity) {
        return new StixRelationship(
                entity.stixId,
                entity.relationshipType,
                entity.sourceRef,
                entity.targetRef,
                new TenantId(entity.ownerTenantId),
                Tlp.valueOf(entity.tlp),
                entity.stixCreated,
                entity.stixModified);
    }
}
