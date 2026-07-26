package com.pte.scheduling.domain;

import com.pte.common.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A local, read-only cache of one authoring {@code ExamSnapshot}'s composition
 * metadata (which task types/sections it contains, in order) — NOT the snapshot
 * content itself (no prompts/answers; those stay in authoring/exam-delivery).
 * Populated by a guarded sync pull at session-creation time until Phase 6 wires
 * the real {@code ExamSnapshotPublished} event consumer (see phase-04 design
 * constraints); the table shape is what a real consumer would populate too.
 */
@Entity
@Table(name = "snapshot_refs")
@Getter
@Setter
@NoArgsConstructor
public class SnapshotRef extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID snapshotPublicId;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private String name;

    @Column
    private UUID tenantId;

    @OneToMany(mappedBy = "snapshotRef", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
    private List<SnapshotRefItem> items = new ArrayList<>();

    public void addItem(SnapshotRefItem item) {
        item.setSnapshotRef(this);
        items.add(item);
    }
}
