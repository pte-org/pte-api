package com.pte.practice.internal.repository;

import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface PracticeSessionRepository extends JpaRepository<PracticeSession, Long> {

    List<PracticeSession> findTop100ByStudentPublicIdAndDeletedFalseOrderByCreatedAtDesc(UUID studentPublicId);

    long countByStudentPublicIdAndDeletedFalse(UUID studentPublicId);

    Optional<PracticeSession> findByStudentPublicIdAndStartIdempotencyKeyAndDeletedFalse(
            UUID studentPublicId, String startIdempotencyKey);

    Optional<PracticeSession> findByPublicIdAndStudentPublicIdAndDeletedFalse(
            UUID publicId, UUID studentPublicId);

    Optional<PracticeSession> findFirstByStudentPublicIdAndMockTestIdAndStatusInAndDeletedFalseOrderByCreatedAtDesc(
            UUID studentPublicId, Long mockTestId, java.util.Collection<PracticeSessionStatus> statuses);

    /** The student's attempts at the given mock tests, newest first, leaving out discarded ones. */
    @Query("""
            select session from PracticeSession session
             where session.studentPublicId = :studentPublicId
               and session.mockTestId in :mockTestIds
               and session.status <> com.pte.practice.internal.domain.enums.PracticeSessionStatus.DISCARDED
               and session.deleted = false
             order by session.createdAt desc
            """)
    List<PracticeSession> findMockTestAttempts(@Param("studentPublicId") UUID studentPublicId,
            @Param("mockTestIds") java.util.Collection<Long> mockTestIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session from PracticeSession session
             where session.publicId = :publicId
               and session.studentPublicId = :studentPublicId
               and session.deleted = false
            """)
    Optional<PracticeSession> findWithLockByPublicIdAndStudentPublicId(
            @Param("publicId") UUID publicId, @Param("studentPublicId") UUID studentPublicId);
}
