package com.aptis.modules.proctor.dto;

import java.time.OffsetDateTime;

/**
 * Plain, framework-independent payload pushed to a proctor's dashboard over STOMP. No
 * framework base class/annotations on purpose (Design Constraint) so a future broker swap
 * doesn't require a schema change. Jackson serializes it natively as a record.
 */
public record ProctorStatusMessage(
        Long attemptId,
        Long examId,
        String studentId,
        String status,
        OffsetDateTime lastHeartbeatAt,
        OffsetDateTime submittedAt,
        OffsetDateTime eventTimestamp) {
}
