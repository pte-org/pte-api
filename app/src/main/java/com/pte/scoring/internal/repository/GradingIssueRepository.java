package com.pte.scoring.internal.repository;

import com.pte.scoring.domain.GradingIssue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GradingIssueRepository extends JpaRepository<GradingIssue, Long> {

    List<GradingIssue> findByTenantIdAndAssignmentIdAndDeletedFalseOrderByReportedAtAsc(
            UUID tenantId, Long assignmentId);

    Optional<GradingIssue> findByTenantIdAndPublicIdAndDeletedFalse(UUID tenantId, UUID publicId);

    Page<GradingIssue> findByTenantIdAndDeletedFalse(UUID tenantId, Pageable pageable);
}
