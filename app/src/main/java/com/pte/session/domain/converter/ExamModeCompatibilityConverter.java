package com.pte.session.domain.converter;

import com.pte.session.domain.enums.ExamMode;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Reads the removed PRACTICE value as a legacy session without reintroducing
 * it to the current host-facing exam-mode contract.
 */
@Converter(autoApply = false)
public class ExamModeCompatibilityConverter implements AttributeConverter<ExamMode, String> {
    private static final String LEGACY_PRACTICE = "PRACTICE";

    @Override
    public String convertToDatabaseColumn(ExamMode attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public ExamMode convertToEntityAttribute(String value) {
        if (value == null || LEGACY_PRACTICE.equals(value)) {
            return null;
        }
        return ExamMode.valueOf(value);
    }
}
