package com.pte.scoring.repository;

import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScoringAnswerRepository extends JpaRepository<ScoringAnswer, Long> {

    Optional<ScoringAnswer> findByAnswerPublicId(UUID answerPublicId);

    List<ScoringAnswer> findBySessionPublicIdAndStatus(UUID sessionPublicId, ScoringAnswerStatus status);

    long countByAttemptPublicIdAndStatus(UUID attemptPublicId, ScoringAnswerStatus status);

    /** True while any row is still in a non-terminal state (PENDING/AI_SCORING/AI_SCORED_PENDING_REVIEW). */
    boolean existsByAttemptPublicIdAndStatusIn(UUID attemptPublicId, List<ScoringAnswerStatus> statuses);
}
