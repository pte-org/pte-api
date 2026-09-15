package com.pte.session.internal.dto.request;

import com.pte.session.internal.constant.SessionConstants;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EnrollStudentRequest(@NotNull(message = SessionConstants.STUDENT_REFERENCE_REQUIRED) UUID studentPublicId) {
}
