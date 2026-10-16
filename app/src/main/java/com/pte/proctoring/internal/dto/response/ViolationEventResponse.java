package com.pte.proctoring.internal.dto.response;

import com.pte.proctoring.domain.enums.ViolationType;

import java.time.Instant;
import java.util.UUID;

public record ViolationEventResponse(UUID publicId, UUID attemptPublicId, ViolationType violationType, String detail,
                                      int sequenceNo, String hash, Instant detectedAt, UUID proctorPublicId) {

    public ViolationEventResponse(UUID publicId, UUID attemptPublicId, ViolationType violationType, String detail,
                                  int sequenceNo, String hash, Instant detectedAt) {
        this(publicId, attemptPublicId, violationType, detail, sequenceNo, hash, detectedAt, null);
    }
}
