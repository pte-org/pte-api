package com.pte.practice.internal.repository;

import com.pte.practice.internal.domain.PracticeExplanationJob;
import com.pte.practice.internal.domain.enums.PracticeExplanationJobStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PracticeExplanationJobRepository extends JpaRepository<PracticeExplanationJob, Long> {

    Optional<PracticeExplanationJob> findByIdAndDeletedFalse(Long id);

    Optional<PracticeExplanationJob> findByExplanationIdAndDeletedFalse(Long explanationId);

    Optional<PracticeExplanationJob> findByStudentPublicIdAndIdempotencyKeyAndDeletedFalse(
            UUID studentPublicId, String idempotencyKey);

    Optional<PracticeExplanationJob> findByPublicIdAndDeletedFalse(UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select job from PracticeExplanationJob job
             where job.publicId = :publicId and job.deleted = false
            """)
    Optional<PracticeExplanationJob> findWithLockByPublicId(@Param("publicId") UUID publicId);

    List<PracticeExplanationJob> findByStatusAndLeaseUntilLessThanEqualAndDeletedFalse(
            PracticeExplanationJobStatus status, Instant now);
}
