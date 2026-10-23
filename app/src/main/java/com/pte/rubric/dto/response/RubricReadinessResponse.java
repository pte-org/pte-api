package com.pte.rubric.dto.response;

import java.util.List;

/** Explicit readiness result so admin users can fix each publication blocker. */
public record RubricReadinessResponse(boolean ready, List<String> blockers) {
}
