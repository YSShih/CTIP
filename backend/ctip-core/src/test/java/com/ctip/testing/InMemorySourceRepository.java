package com.ctip.testing;

import com.ctip.application.port.SourceRepository;
import com.ctip.domain.source.Source;
import com.ctip.domain.source.SourceId;
import com.ctip.sdk.SourceType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 測試用 in-memory SourceRepository:enabledSyncable 由測試指定,save 只記錄呼叫。 */
public final class InMemorySourceRepository implements SourceRepository {

    private List<Source> enabledSyncable = List.of();
    private final List<Source> saved = new ArrayList<>();

    public void enabledSyncable(List<Source> sources) {
        this.enabledSyncable = sources;
    }

    public List<Source> saved() {
        return saved;
    }

    @Override
    public Optional<Source> findById(SourceId id) {
        return enabledSyncable.stream().filter(s -> s.id().equals(id)).findFirst();
    }

    @Override
    public Optional<Source> findBySourceType(SourceType sourceType) {
        return enabledSyncable.stream()
                .filter(s -> s.snapshot().sourceType() == sourceType)
                .findFirst();
    }

    @Override
    public List<Source> findEnabledSyncable() {
        return enabledSyncable;
    }

    @Override
    public List<Source> findAll() {
        return List.copyOf(enabledSyncable);
    }

    @Override
    public Source save(Source source) {
        saved.add(source);
        return source;
    }
}
