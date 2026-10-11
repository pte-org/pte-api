package com.pte.session.dto.response;

import java.util.UUID;

/**
 * Minimal projection of an active student enrollment on an exam session.
 */
public record MonitoringEnrollmentView(
        UUID enrollmentPublicId,
        UUID studentPublicId
) {
}
