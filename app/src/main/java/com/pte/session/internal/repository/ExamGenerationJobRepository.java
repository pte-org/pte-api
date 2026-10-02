package com.pte.session.internal.repository;

import com.pte.session.domain.ExamGenerationJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ExamGenerationJobRepository extends JpaRepository<ExamGenerationJob, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from ExamGenerationJob j join fetch j.session where j.publicId = :publicId")
    Optional<ExamGenerationJob> findWithLockByPublicId(@Param("publicId") UUID publicId);

    Optional<ExamGenerationJob> findBySessionIdAndIdempotencyKey(Long sessionId, String idempotencyKey);

    Optional<ExamGenerationJob> findFirstBySessionIdOrderByCreatedAtDesc(Long sessionId);
}
