package com.ctip.application.bloom;

import com.ctip.application.plan.QuotaService;
import com.ctip.application.port.BloomMemberPort;
import com.ctip.application.port.SubscriptionRepository;
import com.ctip.domain.bloom.BloomParameters;
import com.ctip.domain.bloom.BloomScope;
import com.ctip.domain.plan.QuotaLimit;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.FingerprintAlgorithm;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 決定「這一輪要生成哪些 Bloom、各用什麼參數」(docs/spec/11-sync-bloom.md §11.2)。
 *
 * <p>public 一份;tenant 則是每個持有 ACTIVE 訂閱、且方案的 {@code tenant_bloom_capacity}
 * 不為 null / 0 的租戶各一份。<strong>配額一律經 {@link QuotaService} 讀 plans 表</strong>,
 * 不得在任何地方寫死數值。
 */
@Service
public class BloomScopePlanner {

    private final QuotaService quotas;
    private final SubscriptionRepository subscriptions;
    private final BloomMemberPort members;
    private final BloomSettings settings;

    public BloomScopePlanner(
            QuotaService quotas,
            SubscriptionRepository subscriptions,
            BloomMemberPort members,
            BloomSettings settings) {
        this.quotas = quotas;
        this.subscriptions = subscriptions;
        this.members = members;
        this.settings = settings;
    }

    public List<BloomTarget> targets() {
        List<BloomTarget> targets = new ArrayList<>();
        targets.add(publicTarget());
        for (TenantId tenantId : subscriptions.findActiveTenantIds()) {
            tenantTarget(tenantId).ifPresent(targets::add);
        }
        return targets;
    }

    public BloomTarget publicTarget() {
        return new BloomTarget(
                BloomScope.PUBLIC,
                TenantId.PUBLIC,
                BloomParameters.forCapacity(
                        FingerprintAlgorithm.SHA256, settings.publicCapacity(), settings.falsePositiveRate()));
    }

    /**
     * 尺寸取 {@code min(方案上限, max(預設尺寸, 目前成員數))}:方案值是權利上限(§11.2),
     * 預設尺寸避免為只有幾百筆的租戶配置整份 18MB 陣列(ADR 0024)。
     *
     * <p>⚠️ {@code tenant_bloom_capacity} 的 {@code NULL} 在 §11.2 是「<strong>無</strong> tenant Bloom」,
     * 與平台其他配額欄位的「無限制」相反({@link QuotaLimit} 的通用語意)。此處以 §11.2 為準、
     * fail-closed:只有正整數才產生 tenant bloom。
     */
    public Optional<BloomTarget> tenantTarget(TenantId tenantId) {
        QuotaLimit entitlement = quotas.planFor(tenantId).tenantBloomCapacity();
        if (entitlement.isUnlimited() || entitlement.isDisabled()) {
            return Optional.empty();
        }
        long memberCount = members.countMembers(BloomScope.TENANT, tenantId);
        long sized = Math.max(settings.tenantDefaultCapacity(), memberCount);
        long capacity = Math.min(sized, entitlement.orElse(sized));
        return Optional.of(new BloomTarget(
                BloomScope.TENANT,
                tenantId,
                BloomParameters.forCapacity(FingerprintAlgorithm.SHA256, capacity, settings.falsePositiveRate())));
    }
}
