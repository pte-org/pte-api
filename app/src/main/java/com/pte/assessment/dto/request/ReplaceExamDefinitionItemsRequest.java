package com.pte.assessment.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Replaces a DRAFT template's name + full item list in one call — no partial/incremental item edits (FR-02). */
public record ReplaceExamDefinitionItemsRequest(
        @NotBlank String name,
        @NotNull @Valid List<ExamDefinitionItemRequest> items,
        String templatePolicy) {

    public ReplaceExamDefinitionItemsRequest(String name, List<ExamDefinitionItemRequest> items) {
        this(name, items, null);
    }
}
