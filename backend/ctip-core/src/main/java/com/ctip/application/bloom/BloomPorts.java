package com.ctip.application.bloom;

import com.ctip.application.port.BloomMemberPort;
import com.ctip.application.port.BloomStoragePort;
import com.ctip.application.port.BloomVersionRepository;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.IdGeneratorPort;

/**
 * 三個 Bloom 服務共用的 out-port 組合。
 * 收成 record 是因為 checkstyle 限制建構子參數 ≤ 5(01 §1.8),與 {@code IngestionPipelineConfig.Repositories} 同一前例。
 */
public record BloomPorts(
        BloomMemberPort members,
        BloomVersionRepository versions,
        BloomStoragePort storage,
        ClockPort clock,
        IdGeneratorPort ids) {}
