package com.aptis.modules.scoring.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

@Data
public class GradeAnswerRequest {

    private Boolean isCorrect;

    @DecimalMin("0.00")
    @DecimalMax("999.99")
    private BigDecimal manualScore;

    @AssertTrue(message = "At least one of isCorrect or manualScore must be provided")
    public boolean isValidGrade() {
        return isCorrect != null || manualScore != null;
    }
}
