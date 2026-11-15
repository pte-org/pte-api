package com.pte.identity.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Public profile projection; credentials and Student roster data never cross this boundary.
 * {@code editable} is false for an account shared by several organizations: its personal details belong to all of
 * them, so one Host Admin cannot change them.
 */
public record ExamStaffAccountView(UUID publicId, String fullName, String username, String email,
        String phone, LocalDate dateOfBirth, List<String> roles, String status,
        boolean mustChangePassword, boolean canSendCredentials, boolean editable) {
}
