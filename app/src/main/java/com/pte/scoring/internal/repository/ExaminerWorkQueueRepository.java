package com.pte.scoring.internal.repository;

import com.pte.scoring.domain.GradingAssignment;
import com.pte.scoring.dto.response.ExaminerQueueItemResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.Optional;

/** Examiner-only queue read model; query scope is derived from the authenticated Examiner and tenant. */
public interface ExaminerWorkQueueRepository extends Repository<GradingAssignment, Long> {

    @Query("select assignment from GradingAssignment assignment "
            + "where assignment.tenantId = :tenantId and assignment.sessionPublicId = :sessionPublicId "
            + "and assignment.attemptPublicId = :attemptPublicId and assignment.examinerPublicId = :examinerPublicId "
            + "and assignment.deleted = false")
    Optional<GradingAssignment> findOwnedAttempt(@Param("tenantId") UUID tenantId,
            @Param("sessionPublicId") UUID sessionPublicId,
            @Param("attemptPublicId") UUID attemptPublicId,
            @Param("examinerPublicId") UUID examinerPublicId);

    /** 1-based position of {@code assignment} among this Examiner's assignments in the same session (assigned order). */
    @Query("select count(other.id) from GradingAssignment other "
            + "where other.tenantId = :tenantId and other.examinerPublicId = :examinerPublicId "
            + "and other.sessionPublicId = :sessionPublicId and other.deleted = false "
            + "and (other.assignedAt < :assignedAt or (other.assignedAt = :assignedAt and other.id <= :assignmentId))")
    long countOrdinal(@Param("tenantId") UUID tenantId,
            @Param("examinerPublicId") UUID examinerPublicId,
            @Param("sessionPublicId") UUID sessionPublicId,
            @Param("assignedAt") java.time.Instant assignedAt,
            @Param("assignmentId") Long assignmentId);

    @Query(value = """
            select new com.pte.scoring.dto.response.ExaminerQueueItemResponse(
                assignment.attemptPublicId,
                assignment.sessionPublicId,
                assignment.assignedAt,
                assignment.eligibleAnswerCount,
                (select count(score.id) from ExaminerEvaluation score
                    where score.status = com.pte.scoring.domain.enums.ExaminerAnswerScoreStatus.SUBMITTED
                      and score.tenantId = assignment.tenantId
                      and score.sessionPublicId = assignment.sessionPublicId
                      and score.attemptPublicId = assignment.attemptPublicId),
                case
                    when assignment.eligibleAnswerCount = 0 then 'COMPLETED'
                    when (select count(score.id) from ExaminerEvaluation score
                        where score.status = com.pte.scoring.domain.enums.ExaminerAnswerScoreStatus.SUBMITTED
                          and score.tenantId = assignment.tenantId
                          and score.sessionPublicId = assignment.sessionPublicId
                          and score.attemptPublicId = assignment.attemptPublicId) = 0 then 'PENDING'
                    when (select count(score.id) from ExaminerEvaluation score
                        where score.status = com.pte.scoring.domain.enums.ExaminerAnswerScoreStatus.SUBMITTED
                          and score.tenantId = assignment.tenantId
                          and score.sessionPublicId = assignment.sessionPublicId
                          and score.attemptPublicId = assignment.attemptPublicId) < assignment.eligibleAnswerCount
                        then 'IN_PROGRESS'
                    else 'COMPLETED'
                end)
            from GradingAssignment assignment
            where assignment.tenantId = :tenantId
              and assignment.examinerPublicId = :examinerPublicId
              and assignment.deleted = false
              and not exists (select state.id from ExamSession state
                    where state.tenantId = assignment.tenantId
                      and state.publicId = assignment.sessionPublicId
                      and state.publicationPublicId is not null)
              and (:sessionPublicId is null or assignment.sessionPublicId = :sessionPublicId)
              and (:status = 'ALL'
                or (:status = 'PENDING' and assignment.eligibleAnswerCount > 0
                    and (select count(score.id) from ExaminerEvaluation score
                        where score.status = com.pte.scoring.domain.enums.ExaminerAnswerScoreStatus.SUBMITTED
                          and score.tenantId = assignment.tenantId
                          and score.sessionPublicId = assignment.sessionPublicId
                          and score.attemptPublicId = assignment.attemptPublicId) = 0)
                or (:status = 'IN_PROGRESS'
                    and (select count(score.id) from ExaminerEvaluation score
                        where score.status = com.pte.scoring.domain.enums.ExaminerAnswerScoreStatus.SUBMITTED
                          and score.tenantId = assignment.tenantId
                          and score.sessionPublicId = assignment.sessionPublicId
                          and score.attemptPublicId = assignment.attemptPublicId) > 0
                    and (select count(score.id) from ExaminerEvaluation score
                        where score.status = com.pte.scoring.domain.enums.ExaminerAnswerScoreStatus.SUBMITTED
                          and score.tenantId = assignment.tenantId
                          and score.sessionPublicId = assignment.sessionPublicId
                          and score.attemptPublicId = assignment.attemptPublicId) < assignment.eligibleAnswerCount)
                or (:status = 'COMPLETED'
                    and (assignment.eligibleAnswerCount = 0
                      or (select count(score.id) from ExaminerEvaluation score
                          where score.status = com.pte.scoring.domain.enums.ExaminerAnswerScoreStatus.SUBMITTED
                            and score.tenantId = assignment.tenantId
                            and score.sessionPublicId = assignment.sessionPublicId
                            and score.attemptPublicId = assignment.attemptPublicId) >= assignment.eligibleAnswerCount)))
            order by assignment.assignedAt desc, assignment.id desc
            """,
            countQuery = """
            select count(assignment.id)
            from GradingAssignment assignment
            where assignment.tenantId = :tenantId
              and assignment.examinerPublicId = :examinerPublicId
              and assignment.deleted = false
              and not exists (select state.id from ExamSession state
                    where state.tenantId = assignment.tenantId
                      and state.publicId = assignment.sessionPublicId
                      and state.publicationPublicId is not null)
              and (:sessionPublicId is null or assignment.sessionPublicId = :sessionPublicId)
              and (:status = 'ALL'
                or (:status = 'PENDING' and assignment.eligibleAnswerCount > 0
                    and (select count(score.id) from ExaminerEvaluation score
                        where score.status = com.pte.scoring.domain.enums.ExaminerAnswerScoreStatus.SUBMITTED
                          and score.tenantId = assignment.tenantId
                          and score.sessionPublicId = assignment.sessionPublicId
                          and score.attemptPublicId = assignment.attemptPublicId) = 0)
                or (:status = 'IN_PROGRESS'
                    and (select count(score.id) from ExaminerEvaluation score
                        where score.status = com.pte.scoring.domain.enums.ExaminerAnswerScoreStatus.SUBMITTED
                          and score.tenantId = assignment.tenantId
                          and score.sessionPublicId = assignment.sessionPublicId
                          and score.attemptPublicId = assignment.attemptPublicId) > 0
                    and (select count(score.id) from ExaminerEvaluation score
                        where score.status = com.pte.scoring.domain.enums.ExaminerAnswerScoreStatus.SUBMITTED
                          and score.tenantId = assignment.tenantId
                          and score.sessionPublicId = assignment.sessionPublicId
                          and score.attemptPublicId = assignment.attemptPublicId) < assignment.eligibleAnswerCount)
                or (:status = 'COMPLETED'
                    and (assignment.eligibleAnswerCount = 0
                      or (select count(score.id) from ExaminerEvaluation score
                          where score.status = com.pte.scoring.domain.enums.ExaminerAnswerScoreStatus.SUBMITTED
                            and score.tenantId = assignment.tenantId
                            and score.sessionPublicId = assignment.sessionPublicId
                            and score.attemptPublicId = assignment.attemptPublicId) >= assignment.eligibleAnswerCount)))
            """)
    Page<ExaminerQueueItemResponse> findQueue(@Param("tenantId") UUID tenantId,
            @Param("examinerPublicId") UUID examinerPublicId,
            @Param("sessionPublicId") UUID sessionPublicId,
            @Param("status") String status,
            Pageable pageable);
}
