package com.ctip.testing;

import com.ctip.application.port.BloomMemberPort;
import com.ctip.domain.bloom.BloomScope;
import com.ctip.domain.fingerprint.Fingerprint;
import com.ctip.domain.fingerprint.Sha256FingerprintStrategy;
import com.ctip.domain.tenant.TenantId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** 測試用成員來源;以加入順序推導 id,keyset 分頁語意與 SQL 端一致。 */
public final class InMemoryBloomMembers implements BloomMemberPort {

    private static final Sha256FingerprintStrategy FINGERPRINTS = new Sha256FingerprintStrategy();

    private record Entry(BloomScope scope, TenantId tenantId, UUID id, Fingerprint fingerprint, Instant lastSeen) {}

    private final List<Entry> entries = new ArrayList<>();
    private long sequence;

    /** 加入一名成員,回傳其指紋。 */
    public Fingerprint add(BloomScope scope, TenantId tenantId, String value, Instant lastSeen) {
        Fingerprint fingerprint = FINGERPRINTS.fingerprint(value);
        entries.add(new Entry(scope, tenantId, new UUID(0xb100L, ++sequence), fingerprint, lastSeen));
        return fingerprint;
    }

    @Override
    public List<BloomMember> membersAfter(BloomScope scope, TenantId tenantId, UUID afterId, int limit) {
        return page(scoped(scope, tenantId), afterId, limit);
    }

    @Override
    public List<BloomMember> membersChangedSince(ChangedMembersQuery query) {
        List<Entry> changed = scoped(query.scope(), query.tenantId()).stream()
                .filter(entry -> entry.lastSeen().isAfter(query.since()))
                .toList();
        return page(changed, query.afterId(), query.limit());
    }

    @Override
    public long countMembers(BloomScope scope, TenantId tenantId) {
        return scoped(scope, tenantId).size();
    }

    private List<Entry> scoped(BloomScope scope, TenantId tenantId) {
        return entries.stream()
                .filter(entry -> entry.scope() == scope && entry.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(Entry::id))
                .toList();
    }

    private static List<BloomMember> page(List<Entry> source, UUID afterId, int limit) {
        return source.stream()
                .filter(entry -> afterId == null || entry.id().compareTo(afterId) > 0)
                .limit(limit)
                .map(entry -> new BloomMember(entry.id(), entry.fingerprint()))
                .toList();
    }
}
