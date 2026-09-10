package com.pte.admin.dto.response;

import java.util.List;
import java.util.UUID;

public record ProgramDashboardResponse(
        UUID programPublicId,
        int classCount,
        long studentCount,
        List<ClassStudentCountResponse> classes) {
}
