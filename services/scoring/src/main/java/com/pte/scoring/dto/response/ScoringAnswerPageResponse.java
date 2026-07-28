package com.pte.scoring.dto.response;

import java.util.List;

public record ScoringAnswerPageResponse(
        List<ScoringAnswerResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
