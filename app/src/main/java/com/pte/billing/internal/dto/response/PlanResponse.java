package com.pte.billing.internal.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record PlanResponse(
        UUID publicId,
        String name,
        String description,
        String type,
        BigDecimal price,
        String currency,
        Integer durationDays,
        Integer maxStudentsPerSession,
        Integer extraStudentSlots,
        String status) {
}
