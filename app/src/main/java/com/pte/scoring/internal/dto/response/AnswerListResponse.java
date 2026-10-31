package com.pte.scoring.internal.dto.response;

import java.util.List;

/**
 * Offset-paginated wrapper (Spring Data {@code Pageable}/{@code Page}) — an interactive host review screen over one tenant's data.
 * {@code expectedCount} and {@code answeredCount} are only filled when the list is narrowed to one attempt: how many
 * items the exam gives that attempt and how many of them the student has an answer for (the rest were left blank).
 */
public record AnswerListResponse(List<AnswerListItemResponse> items, int page, int size, long totalElements,
                                  int totalPages, Integer expectedCount, Integer answeredCount) {
}
