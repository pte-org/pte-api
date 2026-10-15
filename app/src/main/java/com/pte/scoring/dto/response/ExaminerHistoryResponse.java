package com.pte.scoring.dto.response;

import java.util.List;

public record ExaminerHistoryResponse(
        List<ExaminerHistoryItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
