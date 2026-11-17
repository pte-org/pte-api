package com.pte.identity.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Public profile projection; credentials and Student roster data never cross this boundary.
 * {@code editable} is kept for clients; since one account belongs to exactly one organization it is always true.
 */
public record ExamStaffAccountView(UUID publicId, String fullName, String username, String email,
        String phone, LocalDate dateOfBirth, List<String> roles, String status,
        boolean mustChangePassword, boolean canSendCredentials, boolean editable) {
}
