package com.aptis.modules.examdelivery.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aptis.modules.examdelivery.domain.RetryRequest;
import com.aptis.modules.examdelivery.domain.RetryRequestStatus;

public interface RetryRequestRepository extends JpaRepository<RetryRequest, Long> {

    boolean existsByStudentIdAndExamIdAndStatusIn(String studentId, Long examId, List<RetryRequestStatus> statuses);

    List<RetryRequest> findByStatus(RetryRequestStatus status);

    /**
     * The exam-start gating check (Design Constraint Step 6). No isolation level declared
     * here: a nested {@code @Transactional} joining an already-open transaction (Spring's
     * default REQUIRED propagation) has its isolation level silently ignored, so
     * READ_COMMITTED is declared explicitly on the caller's outermost transaction boundary
     * instead ({@link com.aptis.modules.examdelivery.service.ExamAttemptService#recordHeartbeat}).
     */
    @Query("SELECT r FROM RetryRequest r WHERE r.studentId = :studentId AND r.examId = :examId "
            + "ORDER BY r.requestedAt DESC LIMIT 1")
    Optional<RetryRequest> findMostRecent(@Param("studentId") String studentId, @Param("examId") Long examId);
}
