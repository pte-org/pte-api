package com.pte.scoring.internal.repository;

import com.pte.scoring.domain.ExaminerAssignmentBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExaminerAssignmentBatchRepository extends JpaRepository<ExaminerAssignmentBatch, Long> {

    Optional<ExaminerAssignmentBatch> findByPublicIdAndTenantIdAndSessionPublicId(
            UUID publicId, UUID tenantId, UUID sessionPublicId);
}
