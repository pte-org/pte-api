package com.pte.itembank.dto.response;

public record QuestionStatsResponse(
        long total,
        long listening,
        long reading,
        long writing,
        long speaking,
        long draft) {
}
