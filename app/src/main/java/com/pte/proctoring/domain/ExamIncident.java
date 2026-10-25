package com.pte.proctoring.domain;

import com.pte.proctoring.domain.enums.IncidentOrigin;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One thing that went wrong, or looked wrong, during an exam session: lockdown evidence the student client
 * detected ({@link IncidentOrigin#AUTO}), a violation a proctor flagged live ({@link IncidentOrigin#PROCTOR_FLAG})
 * or an incident someone reported for handling ({@link IncidentOrigin#REPORTED}). The handling history of a
 * reported incident lives in the audit trail, not in a child table; AUTO rows are deduplicated per attempt by the
 * client's own event id, so a retried upload never records twice.
 */
@Entity
@Table(name = "exam_incidents", uniqueConstraints = {
        @UniqueConstraint(name = "uk_exam_incidents_attempt_client", columnNames = {"attempt_public_id", "client_event_id"})
}, indexes = {
        @Index(name = "idx_exam_incidents_session_origin", columnList = "tenant_id, session_public_id, origin, detected_at"),
        @Index(name = "idx_exam_incidents_attempt", columnList = "attempt_public_id, detected_at")
})
@Getter
@Setter
@NoArgsConstructor
public class ExamIncident extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "session_public_id", nullable = false)
    private UUID sessionPublicId;

    @Column(name = "student_public_id")
    private UUID studentPublicId;

    @Column(name = "attempt_public_id")
    private UUID attemptPublicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IncidentOrigin origin;

    /** Violation type for AUTO and PROCTOR_FLAG rows, the reporter-chosen category for REPORTED rows. */
    @Column(nullable = false, length = 64)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 32)
    private String severity;

    @Column(nullable = false, length = 32)
    private String status = "NEW";

    @Column(name = "reporter_role", length = 32)
    private String reporterRole;

    @Column(name = "reporter_name")
    private String reporterName;

    @Column(name = "reporter_public_id")
    private UUID reporterPublicId;

    @Column(name = "current_owner_role", nullable = false, length = 32)
    private String currentOwnerRole = "HOST_ADMIN";

    @Column(name = "current_owner_name")
    private String currentOwnerName;

    @Column(name = "resolution_summary", columnDefinition = "TEXT")
    private String resolutionSummary;

    /** Client-generated id of an AUTO event; null for every other origin. */
    @Column(name = "client_event_id", length = 128)
    private String clientEventId;

    @Column(name = "client_occurred_at")
    private Instant clientOccurredAt;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;
}
