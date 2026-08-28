package com.ctip.infrastructure.bloom;

import com.ctip.application.port.BloomMemberPort;
import com.ctip.domain.bloom.BloomScope;
import com.ctip.domain.fingerprint.Fingerprint;
import com.ctip.domain.tenant.TenantId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** BloomMemberPort 的 JPA 實作;掃描一律 read-only,且只取投影欄位。 */
@Repository
@Transactional(readOnly = true)
class BloomMemberAdapter implements BloomMemberPort {

    /** keyset 的起點:indicator id 是隨機 UUID,不會等於全零。 */
    private static final UUID FIRST = new UUID(0L, 0L);

    private final BloomMemberJpaRepository jpa;

    BloomMemberAdapter(BloomMemberJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<BloomMember> membersAfter(BloomScope scope, TenantId tenantId, UUID afterId, int limit) {
        UUID after = afterId == null ? FIRST : afterId;
        Limit batch = Limit.of(limit);
        List<BloomMemberRow> rows = scope == BloomScope.PUBLIC
                ? jpa.publicMembersAfter(tenantId.value(), after, batch)
                : jpa.tenantMembersAfter(tenantId.value(), after, batch);
        return toMembers(rows);
    }

    @Override
    public List<BloomMember> membersChangedSince(ChangedMembersQuery query) {
        UUID after = query.afterId() == null ? FIRST : query.afterId();
        UUID tenant = query.tenantId().value();
        Limit batch = Limit.of(query.limit());
        List<BloomMemberRow> rows = query.scope() == BloomScope.PUBLIC
                ? jpa.publicMembersChangedSince(tenant, query.since(), after, batch)
                : jpa.tenantMembersChangedSince(tenant, query.since(), after, batch);
        return toMembers(rows);
    }

    @Override
    public long countMembers(BloomScope scope, TenantId tenantId) {
        return scope == BloomScope.PUBLIC
                ? jpa.countPublicMembers(tenantId.value())
                : jpa.countTenantMembers(tenantId.value());
    }

    private static List<BloomMember> toMembers(List<BloomMemberRow> rows) {
        return rows.stream()
                .map(row -> new BloomMember(row.id(), new Fingerprint(row.fingerprint())))
                .toList();
    }
}
