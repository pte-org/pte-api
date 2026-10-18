package com.pte.scoring.domain;

import com.pte.scoring.domain.enums.ExaminerGradingOperation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** Immutable audit event for examiner grading workflow transitions. */
@Entity
@Table(name = "examiner_grading_events",
        indexes = {
                @Index(name = "idx_examiner_grading_events_work", columnList = "tenant_id, work_id, occurred_at")
        })
@Getter
@NoArgsConstructor
public class ExaminerGradingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private UUID publicId;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "work_id", nullable = false, updatable = false)
    private Long workId;

    @Column(updatable = false)
    private UUID answerPublicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ExaminerGradingOperation operation;

    @Column(nullable = false, updatable = false)
    private long workVersion;

    @Column(updatable = false)
    private Long draftVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", updatable = false)
    private String metadata;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    public ExaminerGradingEvent(UUID tenantId, Long workId, UUID answerPublicId,
            ExaminerGradingOperation operation, long workVersion, Long draftVersion,
            String metadata, Instant occurredAt) {
        this.publicId = UUID.randomUUID();
        this.tenantId = tenantId;
        this.workId = workId;
        this.answerPublicId = answerPublicId;
        this.operation = operation;
        this.workVersion = workVersion;
        this.draftVersion = draftVersion;
        this.metadata = metadata;
        this.occurredAt = occurredAt;
    }

    @PrePersist
    public void ensurePublicId() {
        if (this.publicId == null) {
            this.publicId = UUID.randomUUID();
        }
    }
}
