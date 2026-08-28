package com.ctip.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PlanJpaRepository extends JpaRepository<PlanEntity, UUID> {

    Optional<PlanEntity> findByCode(String code);
}
