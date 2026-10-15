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
@Table(name = "exam_session_incident_history", indexes = {
        @Index(name = "idx_exam_session_inc_hist_incident", columnList = "incident_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class ExamSessionIncidentHistory extends BaseEntity {

    @Column(name = "incident_id", nullable = false)
    private Long incidentId;

    @Column(name = "incident_public_id", nullable = false)
    private UUID incidentPublicId;

    @Column(name = "actor_public_id", nullable = false)
    private UUID actorPublicId;

    @Column(name = "actor_name", nullable = false)
    private String actorName;

    @Column(name = "actor_role", nullable = false, length = 32)
    private String actorRole;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "from_status", length = 32)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 32)
    private String toStatus;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
