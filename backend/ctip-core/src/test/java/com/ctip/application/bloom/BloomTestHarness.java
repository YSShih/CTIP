package com.ctip.application.bloom;

import com.ctip.application.plan.QuotaService;
import com.ctip.domain.bloom.BloomBitArray;
import com.ctip.domain.bloom.BloomChainPolicy;
import com.ctip.domain.bloom.BloomCompression;
import com.ctip.domain.bloom.BloomIndexer;
import com.ctip.domain.bloom.BloomParameters;
import com.ctip.domain.bloom.BloomVersion;
import com.ctip.domain.fingerprint.Fingerprint;
import com.ctip.domain.plan.BillingPeriod;
import com.ctip.domain.plan.PlanCode;
import com.ctip.domain.plan.Subscription;
import com.ctip.domain.plan.SubscriptionId;
import com.ctip.domain.plan.SubscriptionProvider;
import com.ctip.domain.tenant.TenantId;
import com.ctip.testing.CountingRateLimiter;
import com.ctip.testing.FixedClockPort;
import com.ctip.testing.InMemoryBloomMembers;
import com.ctip.testing.InMemoryBloomStorage;
import com.ctip.testing.InMemoryBloomVersionRepository;
import com.ctip.testing.InMemoryPlanRepository;
import com.ctip.testing.InMemorySubscriptionRepository;
import com.ctip.testing.RecordingEventPublisher;
import com.ctip.testing.SequentialIdGenerator;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/**
 * application/bloom 的單元測試共用裝配:全部以記憶體替身組成,不需要資料庫。
 *
 * <p>{@code public} 是因為 {@code application/sync} 的測試也用它——同步端讀的正是這裡生成的
 * 版本與 artifact,另建一份裝配等於讓兩邊的 Bloom 參數各自漂移。
 */
public final class BloomTestHarness {

    public static final Instant NOW = Instant.parse("2026-08-28T04:00:00Z");
    public static final Instant EARLIER = NOW.minusSeconds(86_400);
    public static final TenantId TENANT = new TenantId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    public final InMemoryBloomMembers members = new InMemoryBloomMembers();
    public final InMemoryBloomVersionRepository versions = new InMemoryBloomVersionRepository();
    public final InMemoryBloomStorage storage = new InMemoryBloomStorage();
    public final FixedClockPort clock = FixedClockPort.at(NOW);
    public final SequentialIdGenerator ids = new SequentialIdGenerator();
    public final RecordingEventPublisher events = new RecordingEventPublisher();
    public final BloomChangeTracker changes = new BloomChangeTracker();
    public final InMemoryPlanRepository plans = new InMemoryPlanRepository();
    public final InMemorySubscriptionRepository subscriptions = new InMemorySubscriptionRepository();

    public final BloomPorts ports = new BloomPorts(members, versions, storage, clock, ids);
    public final BloomSettings settings = settings(24, 3);
    public final QuotaService quotas = new QuotaService(plans, subscriptions, new CountingRateLimiter(clock), clock);
    public final BloomScopePlanner planner = new BloomScopePlanner(quotas, subscriptions, members, settings);
    public final BloomArrayLoader loader = new BloomArrayLoader(storage);
    public final BloomSnapshotService snapshots = new BloomSnapshotService(ports, settings, events, changes);
    public final BloomDeltaService deltas = new BloomDeltaService(ports, settings, loader, changes);
    public final BloomRetentionService retention = new BloomRetentionService(ports, settings, planner);

    public static BloomSettings settings(int maxDeltaChain, int artifactKeep) {
        return new BloomSettings(
                1_000L, 0.01, 100L, BloomCompression.NONE, BloomChainPolicy.of(maxDeltaChain), artifactKeep);
    }

    /** 給租戶一份 PREMIUM 訂閱(tenant_bloom_capacity = 1,000,000)。 */
    public void subscribe(TenantId tenantId, PlanCode code) {
        subscriptions.save(Subscription.subscribe(
                new SubscriptionId(ids.nextId()),
                tenantId,
                plans.findByCode(code).orElseThrow(),
                SubscriptionProvider.MANUAL,
                BillingPeriod.openEnded(NOW)));
    }

    public BloomBitArray read(BloomVersion version) {
        return BloomBitArray.of(
                version.parameters(),
                storage.read(
                        version.artifact().storagePath(), version.artifact().compression()));
    }

    public static boolean mightContain(BloomBitArray array, BloomParameters parameters, Fingerprint fingerprint) {
        return Arrays.stream(BloomIndexer.indices(fingerprint, parameters)).allMatch(array::get);
    }
}
