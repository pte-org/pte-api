package com.pte.proctor.dto.response;

import com.pte.proctor.domain.enums.ProctorSessionStatus;

import java.time.Instant;
import java.util.UUID;

public record ProctorSessionResponse(UUID publicId, UUID sessionPublicId, UUID proctorPublicId,
                                      ProctorSessionStatus status, Instant openedAt) {
}
