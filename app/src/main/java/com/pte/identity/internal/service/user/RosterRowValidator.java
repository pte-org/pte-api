package com.pte.identity.internal.service.user;

import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.dto.request.user.BulkCreateUserRow;
import com.pte.identity.internal.dto.response.RosterRowError;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks every roster row against the Student import rules and returns all broken fields at once, so the Host can
 * fix the file in one pass. The web preview runs the same rules; this is the authoritative copy.
 */
class RosterRowValidator {

    private static final Pattern EMAIL = Pattern.compile(IdentityConstants.ROSTER_EMAIL_PATTERN);
    private static final Pattern PHONE = Pattern.compile(IdentityConstants.ROSTER_PHONE_PATTERN);

    private final Clock clock;

    RosterRowValidator(Clock clock) {
        this.clock = clock;
    }

    List<RosterRowError> validate(List<BulkCreateUserRow> rows) {
        List<RosterRowError> errors = new ArrayList<>();
        Set<String> seenEmails = new HashSet<>();
        LocalDate today = LocalDate.now(clock);
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            BulkCreateUserRow row = rows.get(rowIndex);
            validateEmail(rowIndex, row.email(), seenEmails, errors);
            validateFullName(rowIndex, row.fullName(), errors);
            validateLength(rowIndex, row.studentCode(), IdentityConstants.ROSTER_MAX_STUDENT_CODE_LENGTH,
                    IdentityConstants.ROSTER_FIELD_STUDENT_CODE, IdentityConstants.ROSTER_STUDENT_CODE_TOO_LONG, errors);
            validateLength(rowIndex, row.className(), IdentityConstants.ROSTER_MAX_CLASS_NAME_LENGTH,
                    IdentityConstants.ROSTER_FIELD_CLASS_NAME, IdentityConstants.ROSTER_CLASS_NAME_TOO_LONG, errors);
            validatePhone(rowIndex, row.phone(), errors);
            if (row.dateOfBirth() != null && !row.dateOfBirth().isBefore(today)) {
                errors.add(new RosterRowError(rowIndex, IdentityConstants.ROSTER_FIELD_DATE_OF_BIRTH,
                        IdentityConstants.ROSTER_DATE_OF_BIRTH_INVALID));
            }
        }
        return errors;
    }

    private static void validateEmail(int rowIndex, String rawEmail, Set<String> seenEmails,
            List<RosterRowError> errors) {
        String email = UserAccountProvisioner.normalizeOptional(rawEmail);
        String field = IdentityConstants.ROSTER_FIELD_EMAIL;
        if (email == null) {
            errors.add(new RosterRowError(rowIndex, field, IdentityConstants.ROSTER_EMAIL_REQUIRED));
        } else if (email.length() > IdentityConstants.ROSTER_MAX_EMAIL_LENGTH) {
            errors.add(new RosterRowError(rowIndex, field, IdentityConstants.ROSTER_EMAIL_TOO_LONG));
        } else if (!EMAIL.matcher(email).matches()) {
            errors.add(new RosterRowError(rowIndex, field, IdentityConstants.ROSTER_EMAIL_INVALID));
        } else if (!seenEmails.add(email.toLowerCase(Locale.ROOT))) {
            errors.add(new RosterRowError(rowIndex, field, IdentityConstants.DUPLICATE_EMAIL_IN_BATCH));
        }
    }

    private static void validateFullName(int rowIndex, String rawFullName, List<RosterRowError> errors) {
        String fullName = UserAccountProvisioner.normalizeOptional(rawFullName);
        if (fullName == null) {
            errors.add(new RosterRowError(rowIndex, IdentityConstants.ROSTER_FIELD_FULL_NAME,
                    IdentityConstants.ROSTER_FULL_NAME_REQUIRED));
            return;
        }
        validateLength(rowIndex, fullName, IdentityConstants.ROSTER_MAX_FULL_NAME_LENGTH,
                IdentityConstants.ROSTER_FIELD_FULL_NAME, IdentityConstants.ROSTER_FULL_NAME_TOO_LONG, errors);
    }

    private static void validateLength(int rowIndex, String rawValue, int maxLength, String field, String code,
            List<RosterRowError> errors) {
        String value = UserAccountProvisioner.normalizeOptional(rawValue);
        if (value != null && value.length() > maxLength) {
            errors.add(new RosterRowError(rowIndex, field, code));
        }
    }

    private static void validatePhone(int rowIndex, String rawPhone, List<RosterRowError> errors) {
        String phone = UserAccountProvisioner.normalizeOptional(rawPhone);
        if (phone != null && !PHONE.matcher(phone.replaceAll(IdentityConstants.ROSTER_PHONE_SEPARATORS, "")).matches()) {
            errors.add(new RosterRowError(rowIndex, IdentityConstants.ROSTER_FIELD_PHONE,
                    IdentityConstants.ROSTER_PHONE_INVALID));
        }
    }
}
