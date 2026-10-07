package com.pte.practice.internal.repository;

import com.pte.practice.internal.domain.PracticeSessionOperation;
import com.pte.practice.internal.domain.enums.PracticeSessionOperationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PracticeSessionOperationRepository extends JpaRepository<PracticeSessionOperation, Long> {

    Optional<PracticeSessionOperation> findByPracticeSessionIdAndOperationTypeAndIdempotencyKeyAndDeletedFalse(
            Long practiceSessionId, PracticeSessionOperationType operationType, String idempotencyKey);
}
