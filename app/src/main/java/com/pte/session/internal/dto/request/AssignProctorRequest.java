package com.pte.session.internal.dto.request;

import com.pte.session.internal.constant.SessionConstants;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignProctorRequest(@NotNull(message = SessionConstants.PROCTOR_REFERENCE_REQUIRED) UUID proctorPublicId) {
}
