package com.pte.authoring.domain;

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
 * An immutable, versioned freeze of a blueprint at publish time. Downstream
 * services (scheduling, exam-delivery) pin this by {@code publicId} and deep-copy
 * it — later edits to source questions never change a published snapshot
 * (ADR-002/003). Treated as write-once: no mutation after creation.
 */
@Entity
@Table(name = "exam_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class ExamSnapshot extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private UUID sourceBlueprintPublicId;

    @Column
    private UUID tenantId;

    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
    private List<SnapshotItem> items = new ArrayList<>();

    public void addItem(SnapshotItem item) {
        item.setSnapshot(this);
        items.add(item);
    }
}
