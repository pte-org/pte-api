package com.pte.assessment.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
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
@Table(name = "exam_versions", indexes = {
        @Index(name = "idx_exam_versions_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ExamVersion extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private UUID sourceBlueprintPublicId;

    /** The ACTIVE {@code ScoreTemplate} at publish time, pinned forever (spec FR-13) — later template activations never change an already-published exam's scoring. */
    @Column(nullable = false)
    private UUID examDefinitionPublicId;

    @Column(nullable = false)
    private int examDefinitionVersion;

    @Column(name = "generation_algorithm_version", length = 32)
    private String generationAlgorithmVersion;

    @Column(name = "generation_seed")
    private Long generationSeed;

    @Column(name = "pool_policy_fingerprint", length = 128)
    private String poolPolicyFingerprint;

    @Column
    private UUID tenantId;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "exam_version_items", joinColumns = @JoinColumn(name = "exam_version_id"))
    @BatchSize(size = 50)
    @OrderBy("orderIndex ASC")
    private List<ExamVersionItem> items = new ArrayList<>();

    public void addItem(ExamVersionItem item) {
        items.add(item);
    }
}
