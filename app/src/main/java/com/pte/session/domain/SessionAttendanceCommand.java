package com.pte.session.domain;

import com.pte.session.domain.enums.AttendanceStatus;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "session_attendance_commands", uniqueConstraints = {
        @UniqueConstraint(name = "uk_session_attendance_cmd_receipt",
                columnNames = {"tenant_id", "session_id", "actor_public_id", "request_public_id"})
}, indexes = {
        @Index(name = "idx_session_att_cmd_history", columnList = "session_id, student_public_id, created_at"),
        @Index(name = "idx_session_att_cmd_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
public class SessionAttendanceCommand extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ExamSession session;

    @Column(name = "actor_public_id", nullable = false)
    private UUID actorPublicId;

    @Column(name = "actor_role", nullable = false, length = 32)
    private String actorRole;

    @Column(name = "request_public_id", nullable = false)
    private UUID requestPublicId;

    @Column(name = "intent_fingerprint", nullable = false)
    private String intentFingerprint;

    @Column(name = "student_public_id", nullable = false)
    private UUID studentPublicId;

    @Column(name = "enrollment_public_id", nullable = false)
    private UUID enrollmentPublicId;

    @Column(name = "expected_version", nullable = false)
    private Long expectedVersion;

    @Column(name = "applied_version", nullable = false)
    private Long appliedVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 32)
    private AttendanceStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 32)
    private AttendanceStatus newStatus;

    @Column(name = "reason_or_note", length = 1000)
    private String reasonOrNote;

    @Column(name = "server_action_at", nullable = false)
    private Instant serverActionAt;

    @Column(nullable = false)
    private Boolean changed;

    @Column(name = "committed_result", nullable = false, length = 32)
    private String committedResult;
}
