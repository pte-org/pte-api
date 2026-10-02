package com.pte.scoretemplate.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Replaces a DRAFT template's name + full item list in one call — no partial/incremental item edits (FR-02). */
public record ReplaceScoreTemplateItemsRequest(
        @NotBlank String name,
        @NotNull @Valid List<ScoreTemplateItemRequest> items,
        String templatePolicy) {

    public ReplaceScoreTemplateItemsRequest(String name, List<ScoreTemplateItemRequest> items) {
        this(name, items, null);
    }
}
