package com.aptis.modules.proctor.domain;

import java.time.OffsetDateTime;

import com.aptis.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Immutable audit trail row for a privileged proctor action. No setters beyond construction —
 * the application layer never updates or deletes a row once written (Design Constraint).
 */
@Entity
@Table(name = "proctor_action")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(callSuper = true)
public class ProctorAction extends BaseEntity {

    @Column(name = "actor_proctor_id", nullable = false)
    private Long actorProctorId;

    @Column(name = "target_attempt_id")
    private Long targetAttemptId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ProctorActionType actionType;

    @Column(name = "action_timestamp", nullable = false)
    private OffsetDateTime actionTimestamp;

    @Column
    private String details;

    public static ProctorAction create(
            Long actorProctorId, Long targetAttemptId, ProctorActionType actionType, String detailsJson) {
        ProctorAction action = new ProctorAction();
        action.actorProctorId = actorProctorId;
        action.targetAttemptId = targetAttemptId;
        action.actionType = actionType;
        action.actionTimestamp = OffsetDateTime.now();
        action.details = detailsJson;
        return action;
    }
}
