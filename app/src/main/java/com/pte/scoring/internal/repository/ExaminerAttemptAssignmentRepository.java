package com.pte.scoring.internal.repository;

import com.pte.scoring.domain.ExaminerAttemptAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExaminerAttemptAssignmentRepository extends JpaRepository<ExaminerAttemptAssignment, Long> {

    boolean existsByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicId(
            UUID tenantId, UUID sessionPublicId, UUID attemptPublicId, UUID examinerPublicId);

    List<ExaminerAttemptAssignment> findByTenantIdAndSessionPublicIdAndExaminerPublicId(
            UUID tenantId, UUID sessionPublicId, UUID examinerPublicId);
}
