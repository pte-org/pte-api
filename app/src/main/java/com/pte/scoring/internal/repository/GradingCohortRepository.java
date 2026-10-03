package com.pte.scoring.internal.repository;

import com.pte.scoring.domain.GradingCohort;
import com.pte.scoring.domain.enums.GradingCohortStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GradingCohortRepository extends JpaRepository<GradingCohort, Long> {
    Optional<GradingCohort> findByTenantIdAndSessionPublicIdAndDeletedFalse(UUID tenantId, UUID sessionPublicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from GradingCohort c where c.tenantId = :tenantId and c.sessionPublicId = :sessionPublicId "
            + "and c.deleted = false")
    Optional<GradingCohort> findForUpdate(@Param("tenantId") UUID tenantId,
            @Param("sessionPublicId") UUID sessionPublicId);

    List<GradingCohort> findByStatusAndDeletedFalse(GradingCohortStatus status);
}
