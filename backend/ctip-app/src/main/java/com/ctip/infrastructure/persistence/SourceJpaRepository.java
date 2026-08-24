package com.ctip.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** package-private:只有同 package 的 adapter 看得到(docs/spec/01-architecture.md §1.6)。 */
interface SourceJpaRepository extends JpaRepository<SourceEntity, UUID> {

    Optional<SourceEntity> findBySourceType(String sourceType);

    List<SourceEntity> findByEnabledTrueAndSyncableTrue();
}
