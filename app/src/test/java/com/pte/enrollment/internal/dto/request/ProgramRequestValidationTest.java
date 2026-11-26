package com.pte.enrollment.internal.dto.request;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProgramRequestValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void createProgramRequest_sameDayStartAndEndDate_isValid() {
        LocalDate day = LocalDate.of(2026, 11, 25);
        CreateProgramRequest request = new CreateProgramRequest("Khóa 12", "Mô tả", day, day);

        Set<ConstraintViolation<CreateProgramRequest>> violations = VALIDATOR.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void createProgramRequest_endDateBeforeStartDate_isInvalid() {
        CreateProgramRequest request = new CreateProgramRequest(
                "Khóa 12",
                null,
                LocalDate.of(2026, 11, 25),
                LocalDate.of(2026, 11, 24));

        Set<ConstraintViolation<CreateProgramRequest>> violations = VALIDATOR.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly(EnrollmentConstants.PROGRAM_END_DATE_BEFORE_START_DATE);
    }

    @Test
    void updateProgramRequest_sameDayStartAndEndDate_isValid() {
        LocalDate day = LocalDate.of(2026, 11, 25);
        UpdateProgramRequest request = new UpdateProgramRequest("Khóa 12", null, day, day);

        Set<ConstraintViolation<UpdateProgramRequest>> violations = VALIDATOR.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void updateProgramRequest_endDateBeforeStartDate_isInvalid() {
        UpdateProgramRequest request = new UpdateProgramRequest(
                "Khóa 12",
                null,
                LocalDate.of(2026, 11, 25),
                LocalDate.of(2026, 11, 24));

        Set<ConstraintViolation<UpdateProgramRequest>> violations = VALIDATOR.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly(EnrollmentConstants.PROGRAM_END_DATE_BEFORE_START_DATE);
    }
}
