package com.pte.practice.internal.service;

import com.pte.attempt.ResponseConfidence;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PracticeAnswerValidationServiceTest {

    private final PracticeAnswerValidationService service = new PracticeAnswerValidationService();

    @Test
    void answeredPayloadRequiresConfidence() {
        assertThatThrownBy(() -> service.requireConfidenceForAnsweredPayload("answer", null))
                .hasFieldOrPropertyWithValue("code", "PRACTICE_CONFIDENCE_REQUIRED");
    }

    @Test
    void blankPayloadMayRepresentAExplicitSkipWithoutConfidence() {
        assertThatCode(() -> service.requireConfidenceForAnsweredPayload(" ", null))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.requireConfidenceForAnsweredPayload("answer", ResponseConfidence.HIGH))
                .doesNotThrowAnyException();
    }
}
