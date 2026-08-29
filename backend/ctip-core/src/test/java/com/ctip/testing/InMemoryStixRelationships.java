package com.ctip.testing;

import com.ctip.application.port.StixRelationshipPort;
import com.ctip.domain.stix.StixRelationship;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 測試用 in-memory stix_relationships;{@code syncForTarget} 的刪除語意與 adapter 相同。 */
public final class InMemoryStixRelationships implements StixRelationshipPort {

    private final Map<String, StixRelationship> store = new LinkedHashMap<>();

    @Override
    public void syncForTarget(String targetRef, List<StixRelationship> relationships) {
        List<String> stale = new ArrayList<>(store.values().stream()
                .filter(existing -> existing.targetRef().equals(targetRef))
                .filter(existing ->
                        relationships.stream().noneMatch(kept -> kept.stixId().equals(existing.stixId())))
                .map(StixRelationship::stixId)
                .toList());
        stale.forEach(store::remove);
        relationships.forEach(relationship -> store.put(relationship.stixId(), relationship));
    }

    @Override
    public Optional<StixRelationship> findByStixId(String stixId) {
        return Optional.ofNullable(store.get(stixId));
    }

    public List<StixRelationship> all() {
        return List.copyOf(store.values());
    }
}
