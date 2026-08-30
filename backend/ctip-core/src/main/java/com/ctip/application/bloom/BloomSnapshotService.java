package com.ctip.application.bloom;

import com.ctip.application.port.BloomMemberPort.BloomMember;
import com.ctip.application.port.EventPublisherPort;
import com.ctip.domain.bloom.BloomArtifact;
import com.ctip.domain.bloom.BloomArtifactLocation;
import com.ctip.domain.bloom.BloomBitArray;
import com.ctip.domain.bloom.BloomIndexer;
import com.ctip.domain.bloom.BloomStorageKind;
import com.ctip.domain.bloom.BloomVersion;
import com.ctip.domain.bloom.BloomVersionId;
import com.ctip.domain.bloom.BloomVersionSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Full snapshot 的生成(docs/spec/11-sync-bloom.md §11.3,預設每日 04:00)。
 *
 * <p>full snapshot 是<strong>唯一</strong>能反映撤銷與過期的路徑:標準 Bloom 無法移除成員,
 * delta 只能新增位元(§11.3)。每次 full 都起一個新的 {@code datasetVersion},
 * 不變量 L4(參數改變必須換 dataset)因此自動成立。
 *
 * <p>掃描以 keyset 分頁進行、不包在單一交易內:10M 成員的全表掃描不應長時間持有連線。
 * 掃描期間新進的 IOC 可能落在本次之外——Bloom 本就是近似結構,下一次 delta 就會補上。
 */
@Service
public class BloomSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(BloomSnapshotService.class);
    private static final int BATCH = 10_000;
    private static final int MAX_BATCHES = 100_000;

    private final BloomPorts ports;
    private final BloomSettings settings;
    private final EventPublisherPort events;
    private final BloomChangeTracker changes;

    public BloomSnapshotService(
            BloomPorts ports, BloomSettings settings, EventPublisherPort events, BloomChangeTracker changes) {
        this.ports = ports;
        this.settings = settings;
        this.events = events;
        this.changes = changes;
    }

    public BloomVersion generate(BloomTarget target) {
        BloomBitArray array = BloomBitArray.empty(target.parameters());
        long memberCount = fill(target, array);
        Instant now = ports.clock().now();

        Optional<BloomVersion> latest = ports.versions().findLatest(target.scope(), target.tenantId());
        long datasetVersion = latest.map(v -> v.datasetVersion() + 1).orElse(1L);
        BloomArtifact artifact = writeArtifact(target, datasetVersion, array);
        BloomVersionId id = new BloomVersionId(ports.ids().nextId());

        BloomVersion version = latest.map(
                        previous -> previous.nextFullSnapshot(id, target.parameters(), memberCount, artifact, now))
                .orElseGet(() -> BloomVersion.firstSnapshot(new BloomVersionSnapshot(
                        id,
                        target.scope(),
                        target.tenantId(),
                        1L,
                        0L,
                        target.parameters(),
                        memberCount,
                        true,
                        null,
                        now,
                        artifact)));

        BloomVersion saved = ports.versions().save(version);
        version.pullEvents().forEach(events::publish);
        changes.markGenerated(target.scope(), target.tenantId());
        log.info(
                "Bloom full snapshot {}/{} dataset={} members={} bits={}",
                target.scope(),
                target.tenantId().value(),
                saved.datasetVersion(),
                memberCount,
                target.parameters().bitSize());
        return saved;
    }

    private long fill(BloomTarget target, BloomBitArray array) {
        long memberCount = 0;
        UUID after = null;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<BloomMember> page = ports.members().membersAfter(target.scope(), target.tenantId(), after, BATCH);
            for (BloomMember member : page) {
                array.setAll(BloomIndexer.indices(member.fingerprint(), target.parameters()));
                after = member.indicatorId();
                memberCount++;
            }
            if (page.size() < BATCH) {
                return memberCount;
            }
        }
        log.warn("Bloom 掃描達到批次上限 {},{} 的 snapshot 可能不完整", MAX_BATCHES, target.scope());
        return memberCount;
    }

    private BloomArtifact writeArtifact(BloomTarget target, long datasetVersion, BloomBitArray array) {
        var location = new BloomArtifactLocation(target.scope(), target.tenantId(), datasetVersion, 0L, true);
        var stored = ports.storage().write(location, array.toByteArray(), settings.compression());
        return new BloomArtifact(
                BloomStorageKind.FILESYSTEM,
                stored.storagePath(),
                settings.compression(),
                stored.sizeBytes(),
                stored.uncompressedSizeBytes(),
                array.checksum(),
                null,
                0L,
                null);
    }
}
