package com.pte.enrollment.dto.response;

import java.util.UUID;

/** Current class/program context for one tenant student; never historical attempt truth. */
public record StudentAssignmentView(
        UUID classPublicId,
        String className,
        UUID programPublicId,
        String programName) {
}
