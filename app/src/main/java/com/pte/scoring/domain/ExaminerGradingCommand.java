package com.pte.scoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Idempotent command receipt log for examiner mutations. */
@Entity
@Table(name = "examiner_grading_commands",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_examiner_cmd_receipt",
                        columnNames = {"tenant_id", "examiner_public_id", "assignment_public_id", "request_public_id"})
        },
        indexes = {
                @Index(name = "idx_examiner_cmd_assignment", columnList = "tenant_id, assignment_public_id, request_public_id")
        })
@Getter
@NoArgsConstructor
public class ExaminerGradingCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private UUID publicId;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false)
    private UUID examinerPublicId;

    @Column(nullable = false, updatable = false)
    private UUID assignmentPublicId;

    @Column(nullable = false, updatable = false)
    private UUID requestPublicId;

    @Column(nullable = false, updatable = false)
    private String operation;

    @Column(nullable = false, updatable = false)
    private String fingerprint;

    @Column(columnDefinition = "text", nullable = false, updatable = false)
    private String receiptJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public ExaminerGradingCommand(UUID tenantId, UUID examinerPublicId, UUID assignmentPublicId,
            UUID requestPublicId, String operation, String fingerprint, String receiptJson, Instant createdAt) {
        this.publicId = UUID.randomUUID();
        this.tenantId = tenantId;
        this.examinerPublicId = examinerPublicId;
        this.assignmentPublicId = assignmentPublicId;
        this.requestPublicId = requestPublicId;
        this.operation = operation;
        this.fingerprint = fingerprint;
        this.receiptJson = receiptJson;
        this.createdAt = createdAt;
    }

    @PrePersist
    public void ensurePublicId() {
        if (this.publicId == null) {
            this.publicId = UUID.randomUUID();
        }
    }
}
