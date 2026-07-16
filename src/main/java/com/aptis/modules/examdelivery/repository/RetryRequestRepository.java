package com.aptis.modules.examdelivery.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.aptis.modules.examdelivery.domain.RetryRequest;
import com.aptis.modules.examdelivery.domain.RetryRequestStatus;

public interface RetryRequestRepository extends JpaRepository<RetryRequest, Long> {

    boolean existsByStudentIdAndExamIdAndStatusIn(String studentId, Long examId, List<RetryRequestStatus> statuses);

    List<RetryRequest> findByStatus(RetryRequestStatus status);

    /**
     * The exam-start gating check (Design Constraint Step 6): explicit READ_COMMITTED (the
     * default and minimum for Postgres) so this reflects the most recently committed
     * approval, not a stale snapshot from an earlier read in the same longer-lived context.
     */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    @Query("SELECT r FROM RetryRequest r WHERE r.studentId = :studentId AND r.examId = :examId "
            + "ORDER BY r.requestedAt DESC LIMIT 1")
    Optional<RetryRequest> findMostRecent(@Param("studentId") String studentId, @Param("examId") Long examId);
}
