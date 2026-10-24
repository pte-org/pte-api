package com.pte.assessment.domain;

import com.pte.assessment.domain.enums.BlueprintStatus;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A generated exam composition: an ordered set of approved SHARED questions
 * from {@code itembank}. Publishing freezes it into an immutable {@link ExamVersion}.
 */
@Entity
@Table(name = "exam_blueprints", indexes = {
        @Index(name = "idx_blueprints_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ExamBlueprint extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column
    private UUID tenantId;

    /** Academic author; null is retained for legacy ownerless blueprints. */
    @Column(name = "author_user_public_id")
    private UUID authorUserPublicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BlueprintStatus status = BlueprintStatus.DRAFT;

    @Column
    private String rejectionReason;

    @Version
    @Column(nullable = false)
    private long version;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "blueprint_items", joinColumns = @JoinColumn(name = "blueprint_id"))
    @OrderBy("orderIndex ASC")
    private List<BlueprintItem> items = new ArrayList<>();

    public void addItem(BlueprintItem item) {
        items.add(item);
    }
}
