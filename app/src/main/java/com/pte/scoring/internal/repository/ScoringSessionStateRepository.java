package com.pte.scoring.internal.repository;

import com.pte.scoring.domain.ScoringSessionState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ScoringSessionStateRepository extends JpaRepository<ScoringSessionState, Long> {

    Optional<ScoringSessionState> findByTenantIdAndSessionPublicId(UUID tenantId, UUID sessionPublicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ScoringSessionState s WHERE s.tenantId = :tenantId "
            + "AND s.sessionPublicId = :sessionPublicId")
    Optional<ScoringSessionState> findForUpdate(@Param("tenantId") UUID tenantId,
            @Param("sessionPublicId") UUID sessionPublicId);
}
