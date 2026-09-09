package com.pte.admin.domain;

import com.pte.admin.domain.enums.ProgramStatus;
import com.pte.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * The top level of a Host's academic hierarchy under an {@link Organization}
 * (branch) — labeled "Khối" for a School org type or "Khóa" for a Center,
 * same entity either way (label-only difference, driven by Phase 6's
 * FE dictionary). {@code startDate}/{@code endDate} are the minimal
 * term/batch signal (Decision 3) — both null means "always active".
 */
@Entity
@Table(name = "programs", indexes = {@Index(name = "idx_programs_organization", columnList = "organization_id")})
@Getter
@Setter
@NoArgsConstructor
public class Program extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false)
    private String name;

    private String description;

    private LocalDate startDate;

    private LocalDate endDate;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ProgramStatus status = ProgramStatus.ACTIVE;

    /** Both dates null = always active; otherwise {@code today} must fall within the (inclusive) range. */
    public boolean isCurrentlyActive(LocalDate today) {
        boolean afterStart = startDate == null || !today.isBefore(startDate);
        boolean beforeEnd = endDate == null || !today.isAfter(endDate);
        return afterStart && beforeEnd;
    }
}
