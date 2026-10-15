package com.pte.session.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "session_identity_verification_commands", uniqueConstraints = {
        @UniqueConstraint(name = "uk_session_identity_cmd_receipt",
                columnNames = {"tenant_id", "session_id", "actor_public_id", "request_public_id"})
}, indexes = {
        @Index(name = "idx_session_id_cmd_history", columnList = "session_id, student_public_id, created_at"),
        @Index(name = "idx_session_id_cmd_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
public class SessionIdentityVerificationCommand extends BaseEntity {

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

    @Column(name = "previous_status", nullable = false, length = 32)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 32)
    private String newStatus;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "server_action_at", nullable = false)
    private Instant serverActionAt;

    @Column(nullable = false)
    private Boolean changed;

    @Column(name = "committed_result", nullable = false, length = 32)
    private String committedResult;
}
