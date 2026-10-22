package com.pte.practice.internal.dto.response;

import java.util.List;
import java.util.UUID;

public record PracticeReviewResponse(UUID sessionPublicId, List<Item> items) {

    public PracticeReviewResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record Item(UUID publicId, int orderIndex, String taskCode, String displayName, String section,
            String status, String promptText, String savedPayload, String rendererKey) {
    }
}
