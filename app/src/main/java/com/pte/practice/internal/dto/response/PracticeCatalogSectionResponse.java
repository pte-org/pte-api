package com.pte.practice.internal.dto.response;

import java.util.List;

/**
 * One skill of the catalog. {@code practiceQuestionCount} is how many practice-ready questions its task types
 * have; {@code answeredQuestionCount} is how many distinct of them the requesting student has answered. Both are
 * 0 in a catalog that was not built for a student.
 */
public record PracticeCatalogSectionResponse(
        String code,
        String displayName,
        List<PracticeCatalogTaskResponse> taskTypes,
        long practiceQuestionCount,
        long answeredQuestionCount) {

    public PracticeCatalogSectionResponse {
        taskTypes = taskTypes == null ? List.of() : List.copyOf(taskTypes);
    }

    public PracticeCatalogSectionResponse(String code, String displayName, List<PracticeCatalogTaskResponse> taskTypes) {
        this(code, displayName, taskTypes, 0, 0);
    }

    public PracticeCatalogSectionResponse withQuestionCounts(long practiceCount, long answeredCount) {
        return new PracticeCatalogSectionResponse(code, displayName, taskTypes, practiceCount, answeredCount);
    }
}
