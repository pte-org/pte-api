package com.pte.scoring.internal.repository;

import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScoringAnswerRepository extends JpaRepository<ScoringAnswer, Long> {

    Optional<ScoringAnswer> findByAnswerPublicId(UUID answerPublicId);

    List<ScoringAnswer> findBySessionPublicIdAndTenantIdAndStatus(UUID sessionPublicId, UUID tenantId,
            ScoringAnswerStatus status);

    /**
     * Host review list — tenantId is always required (a host is always
     * tenant-scoped). sessionPublicId/status are optional narrowing filters.
     */
    @Query("SELECT s FROM ScoringAnswer s WHERE s.tenantId = :tenantId "
            + "AND (:sessionPublicId IS NULL OR s.sessionPublicId = :sessionPublicId) "
            + "AND (:status IS NULL OR s.status = :status) "
            + "ORDER BY s.createdAt DESC")
    Page<ScoringAnswer> findForReview(@Param("tenantId") UUID tenantId,
            @Param("sessionPublicId") UUID sessionPublicId, @Param("status") ScoringAnswerStatus status,
            Pageable pageable);
}
