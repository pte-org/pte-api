package com.pte.scoring.dto.response;

import java.util.List;

public record ExaminerQueueResponse(
        List<ExaminerQueueItemResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {
}
