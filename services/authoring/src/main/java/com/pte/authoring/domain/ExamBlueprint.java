package com.pte.authoring.domain;

import com.pte.authoring.domain.enums.BlueprintStatus;
import com.pte.common.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
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
 * A reusable exam composition: an ordered set of questions (SHARED and/or the
 * host's own PRIVATE). Publishing freezes it into an immutable {@link ExamSnapshot}.
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BlueprintStatus status = BlueprintStatus.DRAFT;

    @OneToMany(mappedBy = "blueprint", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
    private List<BlueprintItem> items = new ArrayList<>();

    public void addItem(BlueprintItem item) {
        item.setBlueprint(this);
        items.add(item);
    }
}
