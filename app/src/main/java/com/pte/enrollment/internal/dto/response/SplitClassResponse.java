package com.pte.enrollment.internal.dto.response;

import java.util.List;
import java.util.UUID;

public record SplitClassResponse(
        ClassResponse newClass,
        List<UUID> movedStudentPublicIds) {
}
