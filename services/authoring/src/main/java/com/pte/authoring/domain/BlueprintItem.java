package com.pte.authoring.domain;

import com.pte.authoring.domain.enums.PteSection;
import com.pte.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * One slot in a blueprint, referencing a {@link Question} by its {@code publicId}
 * (not a FK — so publish can deep-copy content into an immutable snapshot).
 */
@Entity
@Table(name = "blueprint_items")
@Getter
@Setter
@NoArgsConstructor
public class BlueprintItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blueprint_id", nullable = false)
    private ExamBlueprint blueprint;

    @Column(nullable = false)
    private UUID questionPublicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PteSection section;

    @Column(nullable = false)
    private int orderIndex;
}
