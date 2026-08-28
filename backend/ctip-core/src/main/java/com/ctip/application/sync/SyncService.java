package com.ctip.application.sync;

import com.ctip.application.bloom.BloomPorts;
import com.ctip.application.bloom.BloomScopePlanner;
import com.ctip.application.bloom.BloomSettings;
import com.ctip.application.plan.PlanLimitExceededException;
import com.ctip.application.plan.QuotaService;
import com.ctip.application.port.SyncThrottlePort;
import com.ctip.domain.bloom.BloomCoverage;
import com.ctip.domain.bloom.BloomDeltaCodec;
import com.ctip.domain.bloom.BloomScope;
import com.ctip.domain.bloom.BloomVersion;
import com.ctip.domain.bloom.Checksum;
import com.ctip.domain.tenant.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 增量同步的讀取端(docs/spec/11-sync-bloom.md §11.5、§11.6;09 §9.1「同步」)。
 *
 * <p>生成端在 {@code application/bloom};本服務<strong>只讀</strong>已生成的版本與 artifact,
 * 不觸發任何生成——排程之外的路徑若能觸發 18MB 陣列的重建,就成了放大攻擊的入口。
 *
 * <p>三個端點的授權都依方案(§11.5「下載授權依方案」):
 * public 看 {@code plans.public_bloom_enabled},tenant 看 {@code plans.tenant_bloom_capacity}
 * (判定點在 {@link BloomScopePlanner#hasTenantBloom},與生成端共用)。
 *
 * <p>頻率限制只套在<strong>兩個資料端點</strong>({@code /sync/bloom}、{@code /sync/delta}),
 * manifest 不套:§11.6 的流程第 1 步就是 manifest,client 得先讀它才知道要不要同步、
 * 以及自己的參數是否已作廢。把 manifest 也節流,ANONYMOUS 的 86400 秒等於讓匿名 client
 * 一天只能問一次「有沒有新版本」,而它真正該省的是 18MB 的傳輸,不是幾百 bytes 的 metadata。
 */
@Service
public class SyncService {

    private final BloomPorts ports;
    private final QuotaService quotas;
    private final BloomScopePlanner planner;
    private final BloomSettings settings;
    private final SyncThrottlePort throttle;

    public SyncService(
            BloomPorts ports,
            QuotaService quotas,
            BloomScopePlanner planner,
            BloomSettings settings,
            SyncThrottlePort throttle) {
        this.ports = ports;
        this.quotas = quotas;
        this.planner = planner;
        this.settings = settings;
        this.throttle = throttle;
    }

    /** §11.5:兩層各自的 metadata + 必填的 {@code coverage} / {@code notCovered}。 */
    @Transactional(readOnly = true)
    public SyncManifest manifest(TenantId tenantId) {
        Optional<BloomVersion> publicBloom = quotas.planFor(tenantId).publicBloomEnabled()
                ? ports.versions().findLatest(BloomScope.PUBLIC, TenantId.PUBLIC)
                : Optional.<BloomVersion>empty();
        Optional<BloomVersion> tenantBloom = planner.hasTenantBloom(tenantId)
                ? ports.versions().findLatest(BloomScope.TENANT, tenantId)
                : Optional.<BloomVersion>empty();
        return new SyncManifest(
                publicBloom,
                tenantBloom,
                BloomCoverage.NOT_COVERED,
                settings.chainPolicy().maxDeltaChain());
    }

    /**
     * §11.5 {@code GET /sync/bloom}:直接回二進位串流(儲存體中的原始位元組)。
     *
     * <p>回 {@link Optional#empty()} 代表這個 scope 還沒有任何 snapshot(排程尚未跑過),
     * 由呈現層轉成 {@code 404}——它不是授權問題,授權不足在此之前就已丟 403。
     *
     * <p>刻意<strong>沒有</strong>方法層的交易:各 repository 呼叫自帶交易即足夠,而把 18MB 的
     * 檔案讀取包在同一個交易裡,等於在磁碟 I/O 期間一直握著連線池的連線;
     * 若整段宣告為 {@code readOnly},{@code recordDownload} 的 UPDATE 還會被 PostgreSQL 直接拒絕。
     */
    public Optional<BloomDownload> download(BloomScope scope, TenantId tenantId, String subject) {
        requireEntitlement(scope, tenantId);
        Optional<BloomVersion> latest = ports.versions().findLatestFullSnapshot(scope, tenantId);
        if (latest.isEmpty()) {
            return Optional.empty();
        }
        BloomVersion full = latest.get();
        Duration minInterval = requireSyncInterval(tenantId, subject);
        byte[] content = ports.storage().readStored(full.artifact().storagePath());
        recordSync(subject, minInterval);
        // 04 表 23 的 download_count:有了下載端點它才有呼叫端,否則那一欄永遠是 0(規則 16)
        ports.versions().recordDownload(full.id());
        return Optional.of(new BloomDownload(full, content));
    }

    /**
     * §11.5 {@code GET /sync/delta}:base 到現行最新版本之間新增的位元。
     *
     * <p>請求只帶 {@code base}(§11.5 的參數就只有 {@code base} 與 {@code scope}),
     * 沒有 {@code datasetVersion}——因此 base 一律解讀為「現行 dataset 內的 bloomVersion」。
     * client 若拿舊 dataset 的版號來要 delta,§11.6 第 3 步本來就要求它先比對 dataset:
     * 萬一它沒比對,{@code resultingChecksum} 會對不上而讓它重下 full,不會產生錯誤的本地 Bloom。
     */
    @Transactional(readOnly = true)
    public SyncDelta delta(BloomScope scope, TenantId tenantId, long base, String subject) {
        requireEntitlement(scope, tenantId);
        if (base < 0) {
            throw new IllegalArgumentException("base 不得為負數:" + base);
        }
        BloomVersion full = ports.versions()
                .findLatestFullSnapshot(scope, tenantId)
                .orElseThrow(() -> new SnapshotRequiredException("No bloom snapshot exists yet for this scope"));
        List<BloomVersion> chain = ports.versions().findDeltaChain(scope, tenantId, full.datasetVersion());
        requireChainWithinPolicy(full, chain);
        BloomVersion baseVersion = versionAt(full, chain, base)
                .orElseThrow(() -> new SnapshotRequiredException(
                        "Base version " + base + " is not part of the current dataset, download full snapshot"));
        List<BloomVersion> segment =
                chain.stream().filter(version -> version.bloomVersion() > base).toList();

        Duration minInterval = requireSyncInterval(tenantId, subject);
        byte[] addedBits =
                BloomDeltaCodec.merge(segment.stream().map(this::payloadOf).toList());
        recordSync(subject, minInterval);

        // 空區間(client 已是最新)的 target 就是它自己的 base:arrayChecksum() 仍給得出可驗的值,
        // 而 §11.5 的 resultingChecksum 是必填——沒有可驗的值時 client 無從判斷自己是否還正確
        BloomVersion target = segment.isEmpty() ? baseVersion : segment.get(segment.size() - 1);
        return new SyncDelta(
                scope,
                full.datasetVersion(),
                base,
                target.bloomVersion(),
                addedBits,
                Math.max(0, target.memberCount() - baseVersion.memberCount()),
                Checksum.sha256(addedBits),
                target.arrayChecksum());
    }

    /**
     * §11.3 的兩個門檻,與生成端呼叫的是<strong>同一個</strong>
     * {@link BloomVersion#requiresFullSnapshot} ——差別只在生成端據此改生 full,
     * 這裡據此回 {@code 409 SNAPSHOT_REQUIRED}。
     */
    private void requireChainWithinPolicy(BloomVersion full, List<BloomVersion> chain) {
        long cumulative = chain.stream()
                .mapToLong(version -> version.artifact().uncompressedSizeBytes())
                .sum();
        if (full.requiresFullSnapshot(chain.size(), cumulative, settings.chainPolicy())) {
            throw new SnapshotRequiredException("Delta chain too long, download full snapshot");
        }
    }

    /** base 必須是這條鏈上真的存在的版號:full 本身(0)或其中一段 delta。 */
    private static Optional<BloomVersion> versionAt(BloomVersion full, List<BloomVersion> chain, long base) {
        if (base == full.bloomVersion()) {
            return Optional.of(full);
        }
        return chain.stream().filter(version -> version.bloomVersion() == base).findFirst();
    }

    private byte[] payloadOf(BloomVersion delta) {
        return ports.storage()
                .read(delta.artifact().storagePath(), delta.artifact().compression());
    }

    /** §11.5「下載授權依方案」;兩層各看自己的欄位,判定點與生成端共用。 */
    private void requireEntitlement(BloomScope scope, TenantId tenantId) {
        switch (scope) {
            case PUBLIC -> {
                if (!quotas.planFor(tenantId).publicBloomEnabled()) {
                    throw new PlanLimitExceededException("Public bloom download is not available on this plan");
                }
            }
            case TENANT -> {
                if (!planner.hasTenantBloom(tenantId)) {
                    throw new PlanLimitExceededException("Tenant bloom is not available on this plan");
                }
            }
        }
    }

    /**
     * §11.6 的同步頻率限制。{@code min_sync_interval_seconds = 0} 表示不限制
     * (04 表 17 的約束是 {@code >= 0},而 0 沒有「零秒間隔」以外的讀法)。
     *
     * <p>檢查刻意排在「已確定會回 200」之後:回 409 SNAPSHOT_REQUIRED 的請求若也消耗間隔,
     * client 會照 §11.6 轉去下載 full,卻立刻撞上 429——那條路徑就永遠走不完。
     *
     * @return 該方案的最小間隔,供成功後 {@link #recordSync} 記帳
     */
    private Duration requireSyncInterval(TenantId tenantId, String subject) {
        int seconds = quotas.planFor(tenantId).minSyncIntervalSeconds();
        if (seconds <= 0) {
            return Duration.ZERO;
        }
        Duration minInterval = Duration.ofSeconds(seconds);
        Instant now = ports.clock().now();
        Optional<Instant> last = throttle.lastSyncAt(subject);
        if (last.isPresent()) {
            Duration elapsed = Duration.between(last.get(), now);
            if (elapsed.compareTo(minInterval) < 0) {
                throw new SyncTooFrequentException(minInterval.minus(elapsed), minInterval);
            }
        }
        return minInterval;
    }

    private void recordSync(String subject, Duration minInterval) {
        if (!minInterval.isZero()) {
            throttle.recordSync(subject, ports.clock().now(), minInterval);
        }
    }
}
