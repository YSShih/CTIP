package com.ctip.infrastructure.bloom;

import com.ctip.infrastructure.persistence.IndicatorEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * §11.2 成員條件的 SQL 實作。兩個 scope 的述詞<strong>刻意不同</strong>:
 * tenant 沒有再散布條件(ADR 0019),因為私有 Bloom 只發給該租戶自己。
 *
 * <p>與 {@code BloomMembership} 的 domain 述詞必須等價,由 {@code BloomCoverageTest} 逐筆比對。
 * keyset 分頁以 {@code id > :afterId ORDER BY i.id} 進行;呼叫端首批傳全零 UUID。
 */
interface BloomMemberJpaRepository extends Repository<IndicatorEntity, UUID> {

    String PROJECTION = "select new com.ctip.infrastructure.bloom.BloomMemberRow(i.id, i.fingerprint) ";

    String PUBLIC_MEMBER = """
            from IndicatorEntity i
            where i.ownerTenantId = :tenantId
              and i.tlp = 'CLEAR' and i.status = 'ACTIVE' and i.deletedAt is null
              and exists (select r.id from IndicatorSourceEntity r
                          where r.indicator = i and r.redistributionPolicy <> 'INTERNAL_ONLY')
            """;

    String TENANT_MEMBER = """
            from IndicatorEntity i
            where i.ownerTenantId = :tenantId
              and i.tlp in ('AMBER','AMBER_STRICT') and i.status = 'ACTIVE' and i.deletedAt is null
            """;

    @Query(PROJECTION + PUBLIC_MEMBER + " and i.id > :afterId order by i.id")
    List<BloomMemberRow> publicMembersAfter(
            @Param("tenantId") UUID tenantId, @Param("afterId") UUID afterId, Limit limit);

    @Query(PROJECTION + PUBLIC_MEMBER + " and i.id > :afterId and i.lastSeen > :since order by i.id")
    List<BloomMemberRow> publicMembersChangedSince(
            @Param("tenantId") UUID tenantId,
            @Param("since") Instant since,
            @Param("afterId") UUID afterId,
            Limit limit);

    @Query("select count(i) " + PUBLIC_MEMBER)
    long countPublicMembers(@Param("tenantId") UUID tenantId);

    @Query(PROJECTION + TENANT_MEMBER + " and i.id > :afterId order by i.id")
    List<BloomMemberRow> tenantMembersAfter(
            @Param("tenantId") UUID tenantId, @Param("afterId") UUID afterId, Limit limit);

    @Query(PROJECTION + TENANT_MEMBER + " and i.id > :afterId and i.lastSeen > :since order by i.id")
    List<BloomMemberRow> tenantMembersChangedSince(
            @Param("tenantId") UUID tenantId,
            @Param("since") Instant since,
            @Param("afterId") UUID afterId,
            Limit limit);

    @Query("select count(i) " + TENANT_MEMBER)
    long countTenantMembers(@Param("tenantId") UUID tenantId);
}
