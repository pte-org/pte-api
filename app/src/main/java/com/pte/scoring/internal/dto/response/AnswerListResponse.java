package com.pte.scoring.internal.dto.response;

import java.util.List;

/** Offset-paginated wrapper (Spring Data {@code Pageable}/{@code Page}) — an interactive host review screen over one tenant's data. */
public record AnswerListResponse(List<AnswerListItemResponse> items, int page, int size, long totalElements,
                                  int totalPages) {
}
