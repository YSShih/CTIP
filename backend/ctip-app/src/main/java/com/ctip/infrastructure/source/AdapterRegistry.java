package com.ctip.infrastructure.source;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toUnmodifiableMap;

import com.ctip.application.port.AdapterRegistryPort;
import com.ctip.sdk.SourceType;
import com.ctip.sdk.ThreatSourceAdapter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Adapter 註冊(docs/spec/08-ingestion-sdk.md §8.1):不寫 Factory,Spring 注入集合即可。
 * 同一 SourceType 有兩個實作時,toUnmodifiableMap 於啟動時拋 IllegalStateException——
 * 這是預期行為,不得改成「後者覆蓋前者」。bean 由 AdaptersConfig 提供,韌性裝配已套用。
 */
@Component
public class AdapterRegistry implements AdapterRegistryPort {

    private final Map<SourceType, ThreatSourceAdapter> adapters;

    public AdapterRegistry(List<ThreatSourceAdapter> all) {
        this.adapters = all.stream().collect(toUnmodifiableMap(ThreatSourceAdapter::sourceType, identity()));
    }

    @Override
    public Optional<ThreatSourceAdapter> find(SourceType type) {
        return Optional.ofNullable(adapters.get(type));
    }
}
