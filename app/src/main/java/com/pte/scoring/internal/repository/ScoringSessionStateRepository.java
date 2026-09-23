package com.pte.scoring.internal.repository;

import com.pte.scoring.domain.ScoringSessionState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScoringSessionStateRepository extends JpaRepository<ScoringSessionState, Long> {

    Optional<ScoringSessionState> findByTenantIdAndSessionPublicId(UUID tenantId, UUID sessionPublicId);
}
