package com.aptis.modules.examdelivery.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aptis.modules.examdelivery.domain.ExamAttempt;
import com.aptis.modules.examdelivery.domain.ExamAttemptStatus;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, Long> {

    /** Read-side companion to {@link #markTimedOutAttemptsDisconnected}: same predicate,
     * used only to know which attempts to push a DISCONNECTED notification for. */
    List<ExamAttempt> findByStatusInAndLastHeartbeatAtBefore(
            List<ExamAttemptStatus> statuses, OffsetDateTime cutoff);

    /** Proxy for "students currently in the room" — used for broadcast recipient counts. */
    long countByExamIdAndStatusIn(Long examId, List<ExamAttemptStatus> statuses);

    boolean existsByStudentIdAndExamId(String studentId, Long examId);

    /** How many attempts this student has made on this exam so far — the retry-budget check. */
    long countByStudentIdAndExamId(String studentId, Long examId);

    @EntityGraph(attributePaths = {"answers"})
    @Query("SELECT DISTINCT ea FROM ExamAttempt ea JOIN ea.exam e WHERE ea.isSubmitted = true AND e.organizationId IN :organizationIds ORDER BY ea.submittedAt DESC")
    List<ExamAttempt> findSubmittedByOrgIds(@Param("organizationIds") List<Long> organizationIds);

    // Loads exam association to avoid LazyInitializationException in validateAttemptScope
    @EntityGraph(attributePaths = {"exam"})
    @Query("SELECT ea FROM ExamAttempt ea WHERE ea.id = :id")
    Optional<ExamAttempt> findByIdWithExam(@Param("id") Long id);

    /**
     * Row-locked load for mutating transitions (final submit) — ensures a concurrent
     * student-submit and proctor-force-submit (Phase 8) cannot both apply.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ea FROM ExamAttempt ea WHERE ea.id = :id")
    Optional<ExamAttempt> findByIdForUpdate(@Param("id") Long id);

    /**
     * Proctor-action variant: authorization (assigned proctor) is part of the WHERE clause,
     * not a separate read after the lock — the join to {@code exam} means Postgres locks
     * both the ExamAttempt and its parent Exam row, closing the gap where a concurrent
     * unassignProctor() could otherwise race an in-flight proctor action (CRITICAL Design
     * Constraint, Phase 8: authorization must be atomic with the state change).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ea FROM ExamAttempt ea WHERE ea.id = :id AND ea.exam.proctorId = :proctorId")
    Optional<ExamAttempt> findByIdAndAssignedProctorForUpdate(
            @Param("id") Long id, @Param("proctorId") Long proctorId);

    /**
     * Bulk timeout scan: a single UPDATE affecting every timed-out attempt, rather than
     * loading+saving each entity individually, to avoid lock contention at scale.
     */
    @Modifying
    @Query("UPDATE ExamAttempt ea SET ea.status = com.aptis.modules.examdelivery.domain.ExamAttemptStatus.DISCONNECTED "
            + "WHERE ea.status IN (com.aptis.modules.examdelivery.domain.ExamAttemptStatus.IN_PROGRESS, "
            + "com.aptis.modules.examdelivery.domain.ExamAttemptStatus.SECTION_SWITCH) "
            + "AND ea.lastHeartbeatAt < :cutoff")
    int markTimedOutAttemptsDisconnected(@Param("cutoff") OffsetDateTime cutoff);
}
