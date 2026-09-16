package com.pte.assessment.domain;

import com.pte.itembank.domain.enums.PteSection;
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

/** Immutable section weight copied into a generated snapshot for reporting. */
@Entity
@Table(name = "snapshot_section_weights", uniqueConstraints = {
        @UniqueConstraint(name = "uq_snapshot_section_weights_snapshot_section",
                columnNames = {"snapshot_id", "section"})
}, indexes = {
        @Index(name = "idx_snapshot_section_weights_snapshot", columnList = "snapshot_id")
})
@Getter
@Setter
@NoArgsConstructor
public class SnapshotSectionWeight extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private ExamSnapshot snapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PteSection section;

    @Column(nullable = false)
    private int weightPercent;

    @Column(nullable = false)
    private int orderIndex;
}
