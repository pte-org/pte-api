package com.pte.identity.dto.response;

import java.util.UUID;

/** Minimal active, tenant-scoped identity projection for examiner assignment validation. */
public record ExaminerIdentityView(UUID publicId, String fullName, String email) {
}
