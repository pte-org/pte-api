package com.pte.assessment.dto.response;

import com.pte.itembank.domain.enums.PteSection;

import java.util.List;
import java.util.UUID;

/** Safe reporting contract: snapshot section weights only, never seed or question content. */
public record SnapshotScoringSpec(UUID snapshotPublicId, List<SectionWeight> sectionWeights) {

    public record SectionWeight(PteSection section, int weightPercent, int orderIndex) {
    }
}
