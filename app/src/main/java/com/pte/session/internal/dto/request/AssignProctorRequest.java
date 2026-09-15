package com.pte.session.internal.dto.request;

import com.pte.session.domain.enums.ProctorRole;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** {@code role} is optional — defaults to {@code ASSISTANT_PROCTOR} when omitted. */
public record AssignProctorRequest(@NotNull(message = "Proctor reference is required") UUID proctorPublicId,
                                    ProctorRole role) {
}
