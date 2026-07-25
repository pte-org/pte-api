package com.pte.examdelivery.repository;

import com.pte.examdelivery.domain.AttemptAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, Long> {

    Optional<AttemptAnswer> findByAttemptIdAndPinnedItemId(Long attemptId, Long pinnedItemId);
}
