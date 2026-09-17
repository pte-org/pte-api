package com.pte.assessment.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An immutable, versioned freeze of a blueprint at publish time. Downstream
 * modules ({@code session}, {@code attempt}) pin this by {@code publicId} and
 * read it — later edits to source questions never change a published
 * snapshot. Treated as write-once: no mutation after creation.
 */
@Entity
@Table(name = "exam_snapshots", indexes = {
        @Index(name = "idx_snapshots_tenant", columnList = "tenant_id")
})
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

    /** The ACTIVE {@code ScoreTemplate} at publish time, pinned forever (spec FR-13) — later template activations never change an already-published exam's scoring. */
    @Column(nullable = false)
    private UUID scoreTemplatePublicId;

    @Column(nullable = false)
    private int scoreTemplateVersion;

    @Column
    private UUID tenantId;

    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @OrderBy("orderIndex ASC")
    private List<SnapshotItem> items = new ArrayList<>();

    public void addItem(SnapshotItem item) {
        item.setSnapshot(this);
        items.add(item);
    }
}
