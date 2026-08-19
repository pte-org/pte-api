package com.pte.scheduling.domain;

import com.pte.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One task slot available in a snapshot. {@code taskType}/{@code section} are
 * plain strings (not a shared enum — scheduling never imports authoring's
 * domain types across the service boundary) mirroring authoring's
 * {@code PteTaskType}/{@code PteSection} names.
 */
@Entity
@Table(name = "snapshot_ref_items", indexes = {
        @Index(name = "idx_snapshot_ref_items_ref", columnList = "snapshot_ref_id")
})
@Getter
@Setter
@NoArgsConstructor
public class SnapshotRefItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_ref_id", nullable = false)
    private SnapshotRef snapshotRef;

    @Column(nullable = false)
    private String taskType;

    @Column(nullable = false)
    private String section;

    @Column(nullable = false)
    private int orderIndex;
}
