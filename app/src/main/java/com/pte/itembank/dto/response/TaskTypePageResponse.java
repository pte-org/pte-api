package com.pte.itembank.dto.response;

import java.util.List;

public record TaskTypePageResponse(List<QuestionTypeResponse> items, String nextCursor) {
    public TaskTypePageResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
