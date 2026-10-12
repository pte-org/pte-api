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
@Table(name = "session_attendances", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"session_id", "student_public_id"})
}, indexes = {
        @Index(name = "idx_session_attendances_session", columnList = "session_id"),
        @Index(name = "idx_session_attendances_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
public class SessionAttendance extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ExamSession session;

    @Column(name = "student_public_id", nullable = false)
    private UUID studentPublicId;

    @Column(name = "enrollment_public_id", nullable = false)
    private UUID enrollmentPublicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AttendanceStatus status;

    @Column(nullable = false)
    private Long version = 1L;

    @Column(name = "last_actor_public_id", nullable = false)
    private UUID lastActorPublicId;

    @Column(name = "last_actor_role", nullable = false, length = 32)
    private String lastActorRole;

    @Column(name = "last_action_at", nullable = false)
    private Instant lastActionAt;

    @Column(name = "last_reason_or_note", length = 1000)
    private String lastReasonOrNote;
}
