package com.pte.enrollment.internal.dto.response;

public record StudentSkillPerformanceResponse(
        String skill,
        Integer averageScore,
        boolean available,
        int sampleCount) {
}
