package com.ctip.application.port;

import com.ctip.sdk.SourceType;
import com.ctip.sdk.ThreatSourceAdapter;
import java.util.Optional;

/**
 * Adapter 查找 port。實作為 ctip-app/infrastructure/source/AdapterRegistry
 * (docs/spec/08-ingestion-sdk.md §8.1 的 Spring 集合注入,韌性裝配已於註冊前套用);
 * core 經此取得 adapter,不認識 Spring 也不依賴 ctip-adapters(ADR 0003)。
 */
public interface AdapterRegistryPort {

    Optional<ThreatSourceAdapter> find(SourceType type);
}
