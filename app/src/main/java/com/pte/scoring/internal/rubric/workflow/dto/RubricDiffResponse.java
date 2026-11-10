package com.pte.scoring.internal.rubric.workflow.dto;

import java.util.List;
import java.util.UUID;

public record RubricDiffResponse(UUID expectedActiveVersionPublicId, String diffDigest, List<Change> changes) {
    public RubricDiffResponse { changes = List.copyOf(changes); }
    public record Change(String path, String beforeValue, String afterValue) { }
}
