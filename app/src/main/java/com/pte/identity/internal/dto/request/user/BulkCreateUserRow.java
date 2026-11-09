package com.pte.identity.internal.dto.request.user;

import java.time.LocalDate;

/**
 * One roster row for {@link BulkCreateUsersRequest}, always created as a STUDENT with a server-generated username
 * and password. Field rules (e-mail and full name required, lengths, phone and date formats) are checked for the
 * whole roster at once by {@code RosterRowValidator}, so the Host gets every row error in one response instead of
 * the first bean-validation message only.
 */
public record BulkCreateUserRow(
        String email,
        String fullName,
        String studentCode,
        String className,
        String phone,
        LocalDate dateOfBirth) {
}
