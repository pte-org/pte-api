package com.pte.scheduling.domain;

import com.pte.common.domain.BaseEntity;
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

import java.util.UUID;

/** A proctor assigned to supervise a session, referenced by iam's {@code publicId}. */
@Entity
@Table(name = "proctor_assignments", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"session_id", "proctor_public_id"})
}, indexes = {
        @Index(name = "idx_proctor_assignments_session", columnList = "session_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ProctorAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ExamSession session;

    @Column(name = "proctor_public_id", nullable = false)
    private UUID proctorPublicId;

    @Column(nullable = false)
    private UUID tenantId;
}
