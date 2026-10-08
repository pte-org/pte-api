package com.pte.practice.internal.repository;

import com.pte.practice.internal.domain.PracticeSession;
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
