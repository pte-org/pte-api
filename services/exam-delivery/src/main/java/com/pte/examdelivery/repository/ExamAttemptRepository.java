package com.pte.examdelivery.repository;

import com.pte.examdelivery.domain.ExamAttempt;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, Long> {

    Optional<ExamAttempt> findBySessionPublicIdAndStudentPublicId(UUID sessionPublicId, UUID studentPublicId);

    @EntityGraph(attributePaths = {"pinnedSnapshot", "pinnedSnapshot.items"})
    Optional<ExamAttempt> findWithPinnedByPublicIdAndStudentPublicId(UUID publicId, UUID studentPublicId);

    /** Tenant-scoped, not student-owned — used by {@code ProctorCommandConsumer} (phase-10), where the actor is a verified proctor command, not the student. */
    Optional<ExamAttempt> findByPublicIdAndTenantId(UUID publicId, UUID tenantId);
}
