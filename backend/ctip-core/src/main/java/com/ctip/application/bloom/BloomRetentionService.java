package com.ctip.application.bloom;

import com.ctip.domain.bloom.BloomVersion;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Artifact 保留(docs/spec/11-sync-bloom.md §11.3:最近 {@code BLOOM_ARTIFACT_KEEP} 份,預設 30)。
 *
 * <p>「保留最近 N 份」照字面實作會出事:同一 dataset 內 full snapshot 的 {@code bloomVersion}
 * 最小(= 0),因此是<strong>最舊的一筆</strong>——先被刪掉的會是 full,而它的 delta 還留著,
 * 那條鏈就永遠無法重建。此處在刪除前排除「該 dataset 仍有存活版本」的 full snapshot。
 */
@Service
public class BloomRetentionService {

    private static final Logger log = LoggerFactory.getLogger(BloomRetentionService.class);
    private static final int SCAN_HEADROOM = 200;

    private final BloomPorts ports;
    private final BloomSettings settings;
    private final BloomScopePlanner planner;

    public BloomRetentionService(BloomPorts ports, BloomSettings settings, BloomScopePlanner planner) {
        this.ports = ports;
        this.settings = settings;
        this.planner = planner;
    }

    public void purgeAll() {
        for (BloomTarget target : planner.targets()) {
            try {
                purge(target);
            } catch (RuntimeException e) {
                log.error(
                        "Bloom artifact 保留清理失敗:{} / {}",
                        target.scope(),
                        target.tenantId().value(),
                        e);
            }
        }
    }

    public int purge(BloomTarget target) {
        int keep = settings.artifactKeep();
        List<BloomVersion> newestFirst =
                ports.versions().findNewestFirst(target.scope(), target.tenantId(), keep + SCAN_HEADROOM);
        if (newestFirst.size() <= keep) {
            return 0;
        }
        Set<Long> retainedDatasets = newestFirst.subList(0, keep).stream()
                .map(BloomVersion::datasetVersion)
                .collect(Collectors.toSet());

        int deleted = 0;
        for (BloomVersion version : newestFirst.subList(keep, newestFirst.size())) {
            if (version.isFullSnapshot() && retainedDatasets.contains(version.datasetVersion())) {
                continue;
            }
            ports.storage().delete(version.artifact().storagePath());
            ports.versions().delete(version.id());
            deleted++;
        }
        return deleted;
    }
}
