package com.pte.itembank.internal.generation.domain;

import com.pte.itembank.domain.enums.CefrLevel;

import java.util.List;

/** One row of a batch request, stored as an element of {@code question_generation_batches.rows} (JSONB). */
public record BatchRow(String taskType, CefrLevel cefrLevel, int count, List<String> topicHints, ImageKind imageKind) {

    public BatchRow {
        topicHints = topicHints == null ? List.of() : List.copyOf(topicHints);
        imageKind = imageKind == null ? ImageKind.AUTO : imageKind;
    }
}
