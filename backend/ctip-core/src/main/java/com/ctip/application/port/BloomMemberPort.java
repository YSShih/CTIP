package com.ctip.application.port;

import com.ctip.domain.bloom.BloomScope;
import com.ctip.domain.fingerprint.Fingerprint;
import com.ctip.domain.tenant.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Bloom 成員的掃描 port(docs/spec/11-sync-bloom.md §11.2 的成員條件)。
 *
 * <p>刻意<strong>不重用 {@link IndicatorRepository}</strong>:那八個方法都會 hydrate 完整聚合
 * (含來源記錄),而 full snapshot 的規模是 10M 成員。此處只取 {@code (id, fingerprint)} 投影,
 * 並以 <strong>keyset 分頁</strong>({@code id > afterId ORDER BY id})逐批掃描。
 *
 * <p>成員值一律是 {@code indicators.fingerprint},<strong>不是</strong> {@code normalized_value}
 * ——client 無需傳送原值即可比對。
 *
 * <p>兩個 scope 的述詞不同,{@code TENANT} 沒有再散布條件(ADR 0019);
 * SQL 端的述詞與 {@code BloomMembership} 的 domain 述詞必須等價,由 {@code BloomCoverageTest} 釘住。
 */
public interface BloomMemberPort {

    /** 全量掃描的一批;{@code afterId} 為 null 代表從頭開始。 */
    List<BloomMember> membersAfter(BloomScope scope, TenantId tenantId, UUID afterId, int limit);

    /**
     * 自 {@code since} 之後有新觀測的成員(delta 用)。
     *
     * <p>以 {@code last_seen} 為水位:{@code indicators.updated_at} 沒有索引,而任何使 IOC
     * 成為新成員的路徑(建立、再次回報、過期後復活、手動提交)都會推進 {@code last_seen}。
     * 水位漏掉的部分由每日 full snapshot 收斂——delta 本來就只能新增、不能移除(§11.3)。
     */
    List<BloomMember> membersChangedSince(ChangedMembersQuery query);

    long countMembers(BloomScope scope, TenantId tenantId);

    record BloomMember(UUID indicatorId, Fingerprint fingerprint) {}

    /** 參數收進 record:checkstyle 限制方法參數 ≤ 5(01 §1.8)。 */
    record ChangedMembersQuery(BloomScope scope, TenantId tenantId, Instant since, UUID afterId, int limit) {}
}
