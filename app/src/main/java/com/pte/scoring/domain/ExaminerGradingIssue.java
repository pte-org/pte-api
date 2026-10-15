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

/** Assignment-scoped grading issue (reported by Examiner, resolved by Host). */
@Entity
@Table(name = "examiner_grading_issues",
        indexes = {
                @Index(name = "idx_examiner_grading_issues_work", columnList = "tenant_id, work_id, status")
        })
@Getter
@NoArgsConstructor
public class ExaminerGradingIssue extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(name = "work_id", nullable = false)
    private Long workId;

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

    public ExaminerGradingIssue(UUID tenantId, Long workId, UUID answerPublicId,
            ExaminerGradingIssueType type, String description, UUID reportedBy, Instant reportedAt) {
        this.tenantId = tenantId;
        this.workId = workId;
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
