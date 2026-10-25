package com.pte.proctoring.internal.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Reply to opening a live monitoring; {@code publicId} is the exam session id and {@code status} is always ACTIVE. */
public record ProctorSessionResponse(UUID publicId, UUID sessionPublicId, UUID proctorPublicId, String status,
                                      Instant openedAt) {
}
