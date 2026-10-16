package com.pte.proctoring.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "exam_session_incidents", indexes = {
        @Index(name = "idx_exam_session_incidents_session", columnList = "session_public_id, created_at"),
        @Index(name = "idx_exam_session_incidents_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ExamSessionIncident extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "session_public_id", nullable = false)
    private UUID sessionPublicId;

    @Column(name = "student_public_id")
    private UUID studentPublicId;

    @Column(name = "attempt_public_id")
    private UUID attemptPublicId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 64)
    private String category;

    @Column(nullable = false, length = 32)
    private String severity;

    @Column(nullable = false, length = 32)
    private String status = "NEW";

    @Column(name = "reporter_role", nullable = false, length = 32)
    private String reporterRole;

    @Column(name = "reporter_name", nullable = false)
    private String reporterName;

    @Column(name = "reporter_public_id", nullable = false)
    private UUID reporterPublicId;

    @Column(name = "current_owner_role", nullable = false, length = 32)
    private String currentOwnerRole = "HOST_ADMIN";

    @Column(name = "current_owner_name")
    private String currentOwnerName;

    @Column(name = "resolution_summary", columnDefinition = "TEXT")
    private String resolutionSummary;
}
