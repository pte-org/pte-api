package com.pte.scoring.internal.repository;

import com.pte.scoring.domain.ExaminerGradingWork;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ExaminerGradingWorkRepository extends JpaRepository<ExaminerGradingWork, Long> {

    Optional<ExaminerGradingWork> findByTenantIdAndAssignmentPublicIdAndDeletedFalse(
            UUID tenantId, UUID assignmentPublicId);

    Optional<ExaminerGradingWork> findByTenantIdAndSessionPublicIdAndAttemptPublicIdAndDeletedFalse(
            UUID tenantId, UUID sessionPublicId, UUID attemptPublicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM ExaminerGradingWork w WHERE w.tenantId = :tenantId AND w.assignmentPublicId = :assignmentPublicId AND w.deleted = false")
    Optional<ExaminerGradingWork> findByAssignmentWithLock(
            @Param("tenantId") UUID tenantId,
            @Param("assignmentPublicId") UUID assignmentPublicId);
}
