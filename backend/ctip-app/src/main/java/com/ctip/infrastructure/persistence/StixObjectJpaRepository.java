package com.ctip.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** package-private:Spring Data 細節不外洩(docs/spec/01-architecture.md §1.6)。 */
interface StixObjectJpaRepository extends JpaRepository<StixObjectEntity, UUID> {

    Optional<StixObjectEntity> findByStixId(String stixId);

    List<StixObjectEntity> findByStixIdIn(Collection<String> stixIds);
}
