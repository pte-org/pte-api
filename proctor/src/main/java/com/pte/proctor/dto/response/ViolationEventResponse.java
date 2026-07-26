package com.pte.proctor.dto.response;

import com.pte.proctor.domain.enums.ViolationType;

import java.time.Instant;
import java.util.UUID;

public record ViolationEventResponse(UUID publicId, UUID attemptPublicId, ViolationType violationType, String detail,
                                      int sequenceNo, String hash, Instant detectedAt) {
}
