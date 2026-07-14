package com.aptis.modules.iam.service.studentimport;

import java.util.Optional;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.aptis.modules.iam.constant.IamMessageConstants;

@Service
public class StudentImportValidator {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public Optional<String> validateFieldValues(
            String fullName,
            String email,
            String dateOfBirth) {
        if (fullName == null || fullName.isBlank()) {
            return Optional.of(IamMessageConstants.MISSING_FULL_NAME);
        }
        if (email != null && !email.isBlank() && !EMAIL_PATTERN.matcher(email).matches()) {
            return Optional.of(IamMessageConstants.INVALID_EMAIL_FORMAT);
        }
        if (!isValidDate(dateOfBirth)) {
            return Optional.of(IamMessageConstants.INVALID_DATE_FORMAT);
        }
        return Optional.empty();
    }

    private boolean isValidDate(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException exception) {
            return isValidCustomDate(value);
        }
    }

    private boolean isValidCustomDate(String value) {
        try {
            LocalDate.parse(value, DATE_FORMAT);
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }
}
