package com.pte.scoring.internal.repository;

import com.pte.scoring.domain.ExaminerAnswerScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExaminerAnswerScoreRepository extends JpaRepository<ExaminerAnswerScore, Long> {

    Optional<ExaminerAnswerScore> findByAnswerPublicIdAndTenantId(UUID answerPublicId, UUID tenantId);

    List<ExaminerAnswerScore> findByTenantIdAndSessionPublicId(UUID tenantId, UUID sessionPublicId);

    List<ExaminerAnswerScore> findByTenantIdAndSessionPublicIdAndAttemptPublicId(
            UUID tenantId, UUID sessionPublicId, UUID attemptPublicId);
}
