package com.pte.scoring.internal.repository;

import com.pte.scoring.domain.ScoringAnswerAiDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScoringAnswerAiDetailRepository extends JpaRepository<ScoringAnswerAiDetail, Long> {

    Optional<ScoringAnswerAiDetail> findByAnswerPublicId(UUID answerPublicId);

    /** Read side: always keyed by the verified answer's own tenant, never a client-supplied id. */
    Optional<ScoringAnswerAiDetail> findByAnswerPublicIdAndTenantId(UUID answerPublicId, UUID tenantId);

    List<ScoringAnswerAiDetail> findByTenantIdAndAnswerPublicIdIn(UUID tenantId, Collection<UUID> answerPublicIds);

    /** Only rows that carry a flag; an answer missing from the result has none. */
    @Query("SELECT new com.pte.scoring.internal.repository.AiDetailFlags(d.answerPublicId, d.fallback, "
            + "d.failureReason) FROM ScoringAnswerAiDetail d WHERE d.tenantId = :tenantId "
            + "AND d.answerPublicId IN :answerPublicIds AND (d.fallback = true OR d.failureReason IS NOT NULL)")
    List<AiDetailFlags> findFlags(@Param("tenantId") UUID tenantId,
            @Param("answerPublicIds") Collection<UUID> answerPublicIds);
}
