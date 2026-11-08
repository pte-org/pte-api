package com.pte.itembank.internal.generation.dto;

import com.pte.itembank.PromptDocument;

import java.util.List;

/** Every AI prompt grouped by what it is for, so one page can list them all. */
public record PromptCatalogResponse(int promptVersion, List<Category> categories) {

    /** The prompts of one category: generation, review, scoring or shared blocks. */
    public record Category(String category, List<PromptDocument.Summary> entries) {
    }
}
