package com.pte.scoring.internal.repository;

import com.pte.scoring.domain.ExaminerGradingCommand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExaminerGradingCommandRepository extends JpaRepository<ExaminerGradingCommand, Long> {

    Optional<ExaminerGradingCommand> findByTenantIdAndExaminerPublicIdAndAssignmentPublicIdAndRequestPublicId(
            UUID tenantId, UUID examinerPublicId, UUID assignmentPublicId, UUID requestPublicId);
}
