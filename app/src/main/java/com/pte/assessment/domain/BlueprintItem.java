package com.pte.assessment.domain;

import com.pte.itembank.domain.enums.PteSection;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * One slot in a blueprint, referencing an {@code itembank} question by its
 * {@code publicId} (not a FK — so publish can deep-copy content into an
 * immutable snapshot).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class BlueprintItem {

    @Column(nullable = false)
    private UUID questionPublicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PteSection section;

    @Column(nullable = false)
    private int orderIndex;
}
