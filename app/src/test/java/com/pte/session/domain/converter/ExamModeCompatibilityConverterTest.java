package com.pte.session.domain.converter;

import com.pte.session.domain.enums.ExamMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExamModeCompatibilityConverterTest {
    private final ExamModeCompatibilityConverter converter = new ExamModeCompatibilityConverter();

    @Test
    void readsRemovedPracticeModeAsLegacyState() {
        assertThat(converter.convertToEntityAttribute("PRACTICE")).isNull();
    }

    @Test
    void roundTripsSupportedOfficialMode() {
        assertThat(converter.convertToEntityAttribute("OFFICIAL_EXAM"))
                .isEqualTo(ExamMode.OFFICIAL_EXAM);
        assertThat(converter.convertToDatabaseColumn(ExamMode.OFFICIAL_EXAM))
                .isEqualTo("OFFICIAL_EXAM");
    }

    @Test
    void rejectsUnknownPersistedMode() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
