package com.ctip.config;

import com.ctip.application.bloom.BloomPorts;
import com.ctip.application.bloom.BloomSettings;
import com.ctip.application.port.BloomMemberPort;
import com.ctip.application.port.BloomStoragePort;
import com.ctip.application.port.BloomVersionRepository;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.domain.bloom.BloomChainPolicy;
import com.ctip.infrastructure.bloom.BloomStorageFactory;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bloom 的 port 接線與設定物件(docs/spec/11-sync-bloom.md §11.3;{@code BLOOM_*} 環境變數)。
 * infrastructure 不直接依賴 {@link CtipProperties},與 {@code RateLimitConfig} 同一慣例。
 */
@Configuration(proxyBeanMethods = false)
public class BloomConfig {

    @Bean
    BloomSettings bloomSettings(CtipProperties properties) {
        CtipProperties.Bloom bloom = properties.bloom();
        return new BloomSettings(
                bloom.publicCapacity(),
                bloom.publicFalsePositiveRate(),
                bloom.tenantDefaultCapacity(),
                bloom.compression(),
                BloomChainPolicy.of(bloom.maxDeltaChain()),
                properties.retention().bloomArtifactKeep());
    }

    @Bean
    BloomStoragePort bloomStoragePort(CtipProperties properties) {
        return BloomStorageFactory.filesystem(Path.of(properties.bloom().storageDir()));
    }

    @Bean
    BloomPorts bloomPorts(
            BloomMemberPort members,
            BloomVersionRepository versions,
            BloomStoragePort storage,
            ClockPort clock,
            IdGeneratorPort ids) {
        return new BloomPorts(members, versions, storage, clock, ids);
    }
}
