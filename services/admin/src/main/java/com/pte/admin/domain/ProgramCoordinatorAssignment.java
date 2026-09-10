package com.pte.admin.domain;

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

/**
 * A Program Coordinator assigned to supervise a {@link Program} — same shape
 * as {@link LecturerAssignment}, one level up the hierarchy.
 */
@Entity
@Table(name = "program_coordinator_assignments", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"program_id", "assignee_public_id"})
}, indexes = {
        @Index(name = "idx_coordinator_assignments_program", columnList = "program_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ProgramCoordinatorAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @Column(name = "assignee_public_id", nullable = false)
    private UUID assigneePublicId;

    @Column(nullable = false)
    private UUID tenantId;
}
