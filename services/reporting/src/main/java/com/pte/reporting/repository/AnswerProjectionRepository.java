package com.pte.reporting.repository;

import com.pte.reporting.domain.AnswerProjection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnswerProjectionRepository extends JpaRepository<AnswerProjection, Long> {

    Optional<AnswerProjection> findByAnswerPublicId(UUID answerPublicId);

    List<AnswerProjection> findByAttemptPublicId(UUID attemptPublicId);
}
