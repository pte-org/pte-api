package com.pte.scoring.domain;

import com.pte.scoring.domain.enums.ExaminerGradingIssueStatus;
import com.pte.scoring.domain.enums.ExaminerGradingIssueType;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** A problem the examiner reports on a grading assignment; the host resolves it, and an open one blocks finalizing. */
@Entity
@Table(name = "grading_issues",
        indexes = {
                @Index(name = "idx_grading_issues_assignment", columnList = "tenant_id, assignment_id, status")
        })
@Getter
@NoArgsConstructor
public class GradingIssue extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;

    private UUID answerPublicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExaminerGradingIssueType type;

    @Column(length = 2000, nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExaminerGradingIssueStatus status;

    @Column(nullable = false)
    private UUID reportedBy;

    @Column(nullable = false)
    private Instant reportedAt;

    private UUID resolvedBy;

    private Instant resolvedAt;

    @Column(length = 2000)
    private String resolution;

    public GradingIssue(UUID tenantId, Long assignmentId, UUID answerPublicId,
            ExaminerGradingIssueType type, String description, UUID reportedBy, Instant reportedAt) {
        this.tenantId = tenantId;
        this.assignmentId = assignmentId;
        this.answerPublicId = answerPublicId;
        this.type = type;
        this.description = description;
        this.status = ExaminerGradingIssueStatus.OPEN;
        this.reportedBy = reportedBy;
        this.reportedAt = reportedAt;
    }

    public void resolve(UUID resolvedBy, String resolution, Instant resolvedAt) {
        this.status = ExaminerGradingIssueStatus.RESOLVED;
        this.resolvedBy = resolvedBy;
        this.resolution = resolution;
        this.resolvedAt = resolvedAt;
    }
}
