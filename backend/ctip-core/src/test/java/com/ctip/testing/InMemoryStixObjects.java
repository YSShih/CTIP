package com.ctip.testing;

import com.ctip.application.port.StixObjectPort;
import com.ctip.domain.stix.StixOrigin;
import com.ctip.domain.stix.StixProjection;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 測試用 in-memory stix_objects;content 以「{@code {"stixId":"..."}}」表示,不做真正的 JSON 序列化。 */
public final class InMemoryStixObjects implements StixObjectPort {

    private final Map<String, StixProjection> store = new LinkedHashMap<>();

    @Override
    public Optional<Instant> findCreated(String stixId) {
        return Optional.ofNullable(store.get(stixId)).map(StixProjection::created);
    }

    @Override
    public void upsert(StixProjection projection) {
        StixProjection existing = store.get(projection.stixId());
        store.put(
                projection.stixId(),
                existing == null
                        ? projection
                        : new StixProjection(
                                projection.stixId(),
                                projection.stixType(),
                                projection.ownerTenantId(),
                                projection.indicatorId(),
                                projection.threatId(),
                                projection.tlp(),
                                existing.created(),
                                projection.modified(),
                                projection.content()));
    }

    @Override
    public Optional<String> findContent(String stixId) {
        return Optional.ofNullable(store.get(stixId)).map(projection -> "{\"id\":\"" + projection.stixId() + "\"}");
    }

    @Override
    public Optional<StixOrigin> findOrigin(String stixId) {
        return Optional.ofNullable(store.get(stixId))
                .map(projection -> new StixOrigin(projection.indicatorId(), projection.threatId()));
    }

    @Override
    public Map<String, String> findContents(Collection<String> stixIds) {
        Map<String, String> contents = new LinkedHashMap<>();
        stixIds.forEach(stixId -> findContent(stixId).ifPresent(content -> contents.put(stixId, content)));
        return contents;
    }

    public List<StixProjection> all() {
        return List.copyOf(store.values());
    }

    public List<StixProjection> ofType(String stixType) {
        return store.values().stream()
                .filter(projection -> projection.stixType().equals(stixType))
                .toList();
    }
}
