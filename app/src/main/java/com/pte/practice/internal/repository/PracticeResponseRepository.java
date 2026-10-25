package com.pte.practice.internal.repository;

import com.pte.practice.internal.domain.PracticeResponse;
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

public interface PracticeResponseRepository extends JpaRepository<PracticeResponse, Long> {

    Optional<PracticeResponse> findByIdAndDeletedFalse(Long id);

    Optional<PracticeResponse> findByPublicIdAndDeletedFalse(UUID publicId);

    List<PracticeResponse> findByPracticeSessionIdAndDeletedFalseOrderByOrderIndexAsc(Long practiceSessionId);

    List<PracticeResponse> findByPracticeSessionIdInAndDeletedFalseOrderByPracticeSessionIdAscOrderIndexAsc(
            Collection<Long> practiceSessionIds);

    Optional<PracticeResponse> findByPublicIdAndPracticeSessionIdAndDeletedFalse(
            UUID publicId, Long practiceSessionId);

    /** AI submissions the student started since {@code since}; the per-user rate limit reads this. */
    @Query("""
            select count(item) from PracticeResponse item, PracticeSession owner
             where item.practiceSessionId = owner.id
               and owner.studentPublicId = :studentPublicId
               and item.scoringRequestedAt > :since
            """)
    long countScoringRequestsSince(@Param("studentPublicId") UUID studentPublicId, @Param("since") Instant since);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select item from PracticeResponse item
             where item.publicId = :publicId
               and item.practiceSessionId = :practiceSessionId
               and item.deleted = false
            """)
    Optional<PracticeResponse> findWithLockByPublicIdAndPracticeSessionId(
            @Param("publicId") UUID publicId, @Param("practiceSessionId") Long practiceSessionId);

    /** The session's items still awaiting a score, locked so recovery writes cannot race the worker or a retry. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select item from PracticeResponse item
             where item.practiceSessionId = :practiceSessionId
               and item.deleted = false
               and item.scoringStatus = com.pte.practice.internal.domain.enums.PracticeScoringStatus.SCORING
             order by item.orderIndex
            """)
    List<PracticeResponse> findScoringForUpdate(@Param("practiceSessionId") Long practiceSessionId);
}
