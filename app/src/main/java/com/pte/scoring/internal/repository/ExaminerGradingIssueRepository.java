package com.pte.scoring.internal.repository;

import com.pte.scoring.domain.ExaminerGradingIssue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExaminerGradingIssueRepository extends JpaRepository<ExaminerGradingIssue, Long> {

    List<ExaminerGradingIssue> findByTenantIdAndWorkIdAndDeletedFalseOrderByReportedAtAsc(
            UUID tenantId, Long workId);

    Optional<ExaminerGradingIssue> findByTenantIdAndPublicIdAndDeletedFalse(UUID tenantId, UUID publicId);

    Page<ExaminerGradingIssue> findByTenantIdAndDeletedFalse(UUID tenantId, Pageable pageable);
}
