package com.pte.scoring.internal.repository;

import com.pte.scoring.domain.ExaminerEvaluation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Examiner evaluations: drafts while a work is open and submitted scores once final. Every "score" read filters on
 * SUBMITTED, so a draft never counts as a score.
 */
public interface ExaminerEvaluationRepository extends JpaRepository<ExaminerEvaluation, Long> {

    String SUBMITTED = "com.pte.scoring.domain.enums.ExaminerAnswerScoreStatus.SUBMITTED";

    @Query("select e.answerPublicId as answerPublicId, e.attemptPublicId as attemptPublicId, "
            + "e.sessionPublicId as sessionPublicId, e.examinerPublicId as examinerPublicId, e.status as status, "
            + "e.submittedAt as submittedAt, e.score as score from ExaminerEvaluation e "
            + "where e.tenantId = :tenantId and e.answerPublicId in :answerIds and e.deleted = false "
            + "and e.status = " + SUBMITTED)
    List<StaffScoreRow> findSubmittedRows(@Param("tenantId") UUID tenantId, @Param("answerIds") List<UUID> answerIds);

    @Query("select e.attemptPublicId as attemptPublicId, count(e.id) as scoreCount "
            + "from ExaminerEvaluation e where e.tenantId = :tenantId and e.sessionPublicId in :sessionIds "
            + "and e.examinerPublicId = :examinerId and e.attemptPublicId in :attemptIds and e.deleted = false "
            + "and e.status = " + SUBMITTED + " group by e.attemptPublicId")
    List<StaffScoreCount> countStaffScores(@Param("tenantId") UUID tenantId,
            @Param("sessionIds") List<UUID> sessionIds, @Param("examinerId") UUID examinerId,
            @Param("attemptIds") List<UUID> attemptIds);

    interface StaffScoreCount {
        UUID getAttemptPublicId();

        long getScoreCount();
    }

    /** Submitted scores per attempt for one session, for the host per-attempt examiner progress. */
    @Query("SELECT new com.pte.scoring.internal.repository.AttemptScoreCount(e.attemptPublicId, COUNT(e)) "
            + "FROM ExaminerEvaluation e WHERE e.tenantId = :tenantId AND e.sessionPublicId = :sessionPublicId "
            + "AND e.deleted = false AND e.status = " + SUBMITTED + " GROUP BY e.attemptPublicId")
    List<AttemptScoreCount> countBySessionAndAttempt(@Param("tenantId") UUID tenantId,
            @Param("sessionPublicId") UUID sessionPublicId);

    /** The answer examiner row in any state (draft or submitted). */
    Optional<ExaminerEvaluation> findByAnswerPublicIdAndTenantId(UUID answerPublicId, UUID tenantId);

    /** Submitted score of one answer; empty while only a draft exists. */
    @Query("select e from ExaminerEvaluation e where e.answerPublicId = :answerPublicId and e.tenantId = :tenantId "
            + "and e.deleted = false and e.status = " + SUBMITTED)
    Optional<ExaminerEvaluation> findSubmitted(@Param("answerPublicId") UUID answerPublicId,
            @Param("tenantId") UUID tenantId);

    @Query("select e from ExaminerEvaluation e where e.tenantId = :tenantId and e.sessionPublicId = :sessionPublicId "
            + "and e.deleted = false and e.status = " + SUBMITTED)
    List<ExaminerEvaluation> findSubmittedBySession(@Param("tenantId") UUID tenantId,
            @Param("sessionPublicId") UUID sessionPublicId);

    @Query("select e from ExaminerEvaluation e where e.tenantId = :tenantId and e.sessionPublicId = :sessionPublicId "
            + "and e.attemptPublicId = :attemptPublicId and e.deleted = false and e.status = " + SUBMITTED)
    List<ExaminerEvaluation> findSubmittedByAttempt(@Param("tenantId") UUID tenantId,
            @Param("sessionPublicId") UUID sessionPublicId, @Param("attemptPublicId") UUID attemptPublicId);

    @Query("select e from ExaminerEvaluation e where e.tenantId = :tenantId and e.examinerPublicId = :examinerPublicId "
            + "and e.requestPublicId = :requestPublicId and e.deleted = false and e.status = " + SUBMITTED)
    Optional<ExaminerEvaluation> findSubmittedByRequest(@Param("tenantId") UUID tenantId,
            @Param("examinerPublicId") UUID examinerPublicId, @Param("requestPublicId") UUID requestPublicId);

    @Query("select count(e) from ExaminerEvaluation e where e.tenantId = :tenantId "
            + "and e.sessionPublicId = :sessionPublicId and e.attemptPublicId = :attemptPublicId "
            + "and e.examinerPublicId = :examinerPublicId and e.deleted = false and e.status = " + SUBMITTED)
    long countSubmitted(@Param("tenantId") UUID tenantId, @Param("sessionPublicId") UUID sessionPublicId,
            @Param("attemptPublicId") UUID attemptPublicId, @Param("examinerPublicId") UUID examinerPublicId);

    // ---- drafts (the same rows, addressed by the work they belong to) ----

    List<ExaminerEvaluation> findByTenantIdAndAssignmentIdAndDeletedFalse(UUID tenantId, Long assignmentId);

    /** Saved drafts per grading work, for the host per-attempt examiner progress. */
    @Query("SELECT new com.pte.scoring.internal.repository.DraftCount(e.assignmentId, COUNT(e)) "
            + "FROM ExaminerEvaluation e WHERE e.tenantId = :tenantId AND e.assignmentId IN :assignmentIds "
            + "AND e.deleted = false GROUP BY e.assignmentId")
    List<DraftCount> countByAssignment(@Param("tenantId") UUID tenantId, @Param("assignmentIds") List<Long> assignmentIds);

    Optional<ExaminerEvaluation> findByTenantIdAndAssignmentIdAndAnswerPublicIdAndDeletedFalse(
            UUID tenantId, Long assignmentId, UUID answerPublicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM ExaminerEvaluation e WHERE e.tenantId = :tenantId AND e.assignmentId = :assignmentId "
            + "AND e.answerPublicId = :answerPublicId AND e.deleted = false")
    Optional<ExaminerEvaluation> findByAssignmentAndAnswerWithLock(@Param("tenantId") UUID tenantId,
            @Param("assignmentId") Long assignmentId, @Param("answerPublicId") UUID answerPublicId);

    /** The answer examiner row in any state, locked so a draft save and a submit cannot interleave. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM ExaminerEvaluation e WHERE e.tenantId = :tenantId AND e.answerPublicId = :answerPublicId "
            + "AND e.deleted = false")
    Optional<ExaminerEvaluation> findByAnswerWithLock(@Param("tenantId") UUID tenantId,
            @Param("answerPublicId") UUID answerPublicId);
}
