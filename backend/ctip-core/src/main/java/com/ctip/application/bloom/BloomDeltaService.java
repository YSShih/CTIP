package com.ctip.application.bloom;

import com.ctip.application.port.BloomMemberPort.BloomMember;
import com.ctip.application.port.BloomMemberPort.ChangedMembersQuery;
import com.ctip.domain.bloom.BloomArtifact;
import com.ctip.domain.bloom.BloomArtifactLocation;
import com.ctip.domain.bloom.BloomBitArray;
import com.ctip.domain.bloom.BloomDeltaCodec;
import com.ctip.domain.bloom.BloomIndexer;
import com.ctip.domain.bloom.BloomParameters;
import com.ctip.domain.bloom.BloomStorageKind;
import com.ctip.domain.bloom.BloomVersion;
import com.ctip.domain.bloom.BloomVersionId;
import com.ctip.domain.bloom.Checksum;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Delta 的生成(docs/spec/11-sync-bloom.md §11.3,預設每小時)。
 *
 * <p>delta = 從 base 到 target 之間<strong>新增</strong>的 bit 索引集合。撤銷與過期無法透過
 * delta 移除,只有 full snapshot 會反映(§11.3)——這也是本服務只收集「由 0 變 1」的位元的原因。
 *
 * <p>{@code resultingChecksum} 是套用本段 delta 後<strong>整個位元陣列</strong>的 SHA-256:
 * client 套用後自我驗證,不符即丟棄並重下 full(§11.6)。
 */
@Service
public class BloomDeltaService {

    private static final Logger log = LoggerFactory.getLogger(BloomDeltaService.class);
    private static final int BATCH = 10_000;
    private static final int MAX_BATCHES = 10_000;
    /** 水位往回退一分鐘:重複套用已存在的位元不會產生任何效果,漏掉成員則會造成 false negative。 */
    private static final Duration WATERMARK_OVERLAP = Duration.ofMinutes(1);

    private final BloomPorts ports;
    private final BloomSettings settings;
    private final BloomArrayLoader loader;
    private final BloomChangeTracker changes;

    public BloomDeltaService(
            BloomPorts ports, BloomSettings settings, BloomArrayLoader loader, BloomChangeTracker changes) {
        this.ports = ports;
        this.settings = settings;
        this.loader = loader;
        this.changes = changes;
    }

    public DeltaOutcome generate(BloomTarget target) {
        Optional<BloomVersion> baseline = ports.versions().findLatestFullSnapshot(target.scope(), target.tenantId());
        if (baseline.isEmpty()) {
            return DeltaOutcome.of(DeltaOutcome.Status.NO_BASELINE);
        }
        BloomVersion full = baseline.get();
        List<BloomVersion> chain =
                ports.versions().findDeltaChain(target.scope(), target.tenantId(), full.datasetVersion());
        if (escalatesToFullSnapshot(full, chain, target.parameters())) {
            return DeltaOutcome.of(DeltaOutcome.Status.FULL_SNAPSHOT_REQUIRED);
        }
        if (!changes.hasChanges(target.scope(), target.tenantId())) {
            return DeltaOutcome.of(DeltaOutcome.Status.NO_CHANGES);
        }
        return append(target, full, chain);
    }

    private boolean escalatesToFullSnapshot(BloomVersion full, List<BloomVersion> chain, BloomParameters wanted) {
        long cumulative = chain.stream()
                .mapToLong(version -> version.artifact().uncompressedSizeBytes())
                .sum();
        return full.requiresFullSnapshot(chain.size(), cumulative, settings.chainPolicy())
                || !full.isCompatibleWith(wanted);
    }

    private DeltaOutcome append(BloomTarget target, BloomVersion full, List<BloomVersion> chain) {
        BloomVersion latest = chain.isEmpty() ? full : chain.get(chain.size() - 1);
        BloomBitArray array;
        try {
            array = loader.load(full, chain);
        } catch (BloomArtifactCorruptedException e) {
            // 以損壞的陣列算出的 resultingChecksum 會讓每個 client 套用後失敗,改為重建 full snapshot
            log.error(
                    "Bloom {}/{} 的既有 artifact 損壞,改以 full snapshot 重建",
                    target.scope(),
                    target.tenantId().value(),
                    e);
            return DeltaOutcome.of(DeltaOutcome.Status.FULL_SNAPSHOT_REQUIRED);
        }
        Instant since = latest.generatedAt().minus(WATERMARK_OVERLAP);
        List<Long> added = collectNewBits(target, full.parameters(), array, since);
        if (added.isEmpty()) {
            changes.markGenerated(target.scope(), target.tenantId());
            return DeltaOutcome.of(DeltaOutcome.Status.NO_CHANGES);
        }

        byte[] payload = BloomDeltaCodec.encode(added);
        BloomArtifact artifact =
                writeArtifact(target, full.datasetVersion(), latest.bloomVersion() + 1, payload, array.checksum());
        BloomVersion delta = latest.nextDelta(
                new BloomVersionId(ports.ids().nextId()),
                ports.members().countMembers(target.scope(), target.tenantId()),
                artifact,
                ports.clock().now());

        BloomVersion saved = ports.versions().save(delta);
        changes.markGenerated(target.scope(), target.tenantId());
        log.info(
                "Bloom delta {}/{} dataset={} version={} addedBits={}",
                target.scope(),
                target.tenantId().value(),
                saved.datasetVersion(),
                saved.bloomVersion(),
                added.size());
        return DeltaOutcome.created(saved);
    }

    private List<Long> collectNewBits(
            BloomTarget target, BloomParameters parameters, BloomBitArray array, Instant since) {
        List<Long> added = new ArrayList<>();
        UUID after = null;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<BloomMember> page = ports.members()
                    .membersChangedSince(
                            new ChangedMembersQuery(target.scope(), target.tenantId(), since, after, BATCH));
            for (BloomMember member : page) {
                added.addAll(array.setAll(BloomIndexer.indices(member.fingerprint(), parameters)));
                after = member.indicatorId();
            }
            if (page.size() < BATCH) {
                return added;
            }
        }
        log.warn("Bloom delta 掃描達到批次上限 {},{} 的本段 delta 可能不完整", MAX_BATCHES, target.scope());
        return added;
    }

    private BloomArtifact writeArtifact(
            BloomTarget target, long datasetVersion, long bloomVersion, byte[] payload, Checksum resulting) {
        var location =
                new BloomArtifactLocation(target.scope(), target.tenantId(), datasetVersion, bloomVersion, false);
        var stored = ports.storage().write(location, payload, settings.compression());
        return new BloomArtifact(
                BloomStorageKind.FILESYSTEM,
                stored.storagePath(),
                settings.compression(),
                stored.sizeBytes(),
                stored.uncompressedSizeBytes(),
                Checksum.sha256(payload),
                resulting,
                0L,
                null);
    }
}
