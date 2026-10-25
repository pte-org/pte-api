package com.pte.practice.internal.repository;

import com.pte.practice.internal.domain.PracticeSessionItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import java.util.UUID;

public interface PracticeSessionItemRepository extends JpaRepository<PracticeSessionItem, Long> {

    Optional<PracticeSessionItem> findByIdAndDeletedFalse(Long id);

    Optional<PracticeSessionItem> findByPublicIdAndDeletedFalse(UUID publicId);

    List<PracticeSessionItem> findByPracticeSessionIdAndDeletedFalseOrderByOrderIndexAsc(Long practiceSessionId);

    List<PracticeSessionItem> findByPracticeSessionIdInAndDeletedFalseOrderByPracticeSessionIdAscOrderIndexAsc(
            Collection<Long> practiceSessionIds);

    Optional<PracticeSessionItem> findByPublicIdAndPracticeSessionIdAndDeletedFalse(
            UUID publicId, Long practiceSessionId);

    /** AI submissions the student started since {@code since}; the per-user rate limit reads this. */
    @Query("""
            select count(item) from PracticeSessionItem item, PracticeSession owner
             where item.practiceSessionId = owner.id
               and owner.studentPublicId = :studentPublicId
               and item.scoringRequestedAt > :since
            """)
    long countScoringRequestsSince(@Param("studentPublicId") UUID studentPublicId, @Param("since") Instant since);

    /** Questions this student has already had in any practice session, so new sessions can prefer unseen ones. */
    @Query("""
            select distinct item.sourceQuestionPublicId from PracticeSessionItem item, PracticeSession owner
             where item.practiceSessionId = owner.id
               and owner.studentPublicId = :studentPublicId
               and item.taskCode in :taskCodes
               and item.sourceQuestionPublicId is not null
               and item.deleted = false
            """)
    List<UUID> findSeenSourceQuestionIds(@Param("studentPublicId") UUID studentPublicId,
            @Param("taskCodes") Collection<String> taskCodes);

    List<PracticeSessionItem> findByPracticeSessionIdInAndDeletedFalse(Collection<Long> practiceSessionIds);

    /** Distinct questions the student has answered, per task code: {@code [taskCode, count]} rows. */
    @Query("""
            select item.taskCode, count(distinct item.sourceQuestionPublicId) from PracticeSessionItem item, PracticeSession owner
             where item.practiceSessionId = owner.id
               and owner.studentPublicId = :studentPublicId
               and item.status = com.pte.practice.internal.domain.enums.PracticeSessionItemStatus.ANSWERED
               and item.sourceQuestionPublicId is not null
               and item.deleted = false
             group by item.taskCode
            """)
    List<Object[]> countAnsweredSourceQuestionsByTaskCode(@Param("studentPublicId") UUID studentPublicId);

    /** Mean score (0-100) of the student's scored answers per task code: {@code [taskCode, average]} rows. */
    @Query("""
            select item.taskCode, avg(coalesce(item.aiScore, item.objectiveScore)) from PracticeSessionItem item, PracticeSession owner
             where item.practiceSessionId = owner.id
               and owner.studentPublicId = :studentPublicId
               and item.status = com.pte.practice.internal.domain.enums.PracticeSessionItemStatus.ANSWERED
               and coalesce(item.aiScore, item.objectiveScore) is not null
               and item.deleted = false
             group by item.taskCode
            """)
    List<Object[]> averageScoreByTaskCode(@Param("studentPublicId") UUID studentPublicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select item from PracticeSessionItem item
             where item.publicId = :publicId
               and item.practiceSessionId = :practiceSessionId
               and item.deleted = false
            """)
    Optional<PracticeSessionItem> findWithLockByPublicIdAndPracticeSessionId(
            @Param("publicId") UUID publicId, @Param("practiceSessionId") Long practiceSessionId);

    /** The session's items still awaiting a score, locked so recovery writes cannot race the worker or a retry. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select item from PracticeSessionItem item
             where item.practiceSessionId = :practiceSessionId
               and item.deleted = false
               and item.scoringStatus = com.pte.practice.internal.domain.enums.PracticeScoringStatus.SCORING
             order by item.orderIndex
            """)
    List<PracticeSessionItem> findScoringForUpdate(@Param("practiceSessionId") Long practiceSessionId);
}
