package com.pte.practice.internal.service;

import com.pte.attempt.ResponseConfidence;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PracticeAnswerValidationServiceTest {

    private final PracticeAnswerValidationService service = new PracticeAnswerValidationService(
            JsonMapper.builder().build());

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

    @Test
    void objectivePayloadMustMatchTheRendererContract() {
        assertThatCode(() -> service.validate("MC_READING_SINGLE_V1",
                "{\"selectedOption\":\"a\"}", ResponseConfidence.MEDIUM))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> service.validate("MC_READING_SINGLE_V1",
                "{\"selectedOptions\":[]}", ResponseConfidence.MEDIUM))
                .hasFieldOrPropertyWithValue("code", "PRACTICE_ANSWER_INVALID");
    }

    @Test
    void draftPayloadMayOmitConfidenceUntilTheStudentSubmits() {
        assertThatCode(() -> service.validateDraft("MC_READING_SINGLE_V1",
                1, "{\"selectedOption\":\"a\"}", null))
                .doesNotThrowAnyException();
    }

    @Test
    void unsupportedMediaRendererIsNotAcceptedAsAnObjectiveAnswer() {
        assertThatThrownBy(() -> service.validate("MC_LISTENING_SINGLE_V1",
                "{\"text\":\"hello\"}", ResponseConfidence.HIGH))
                .hasFieldOrPropertyWithValue("code", "PRACTICE_UNSUPPORTED_RUNTIME");
    }

    @Test
    void recordingPayloadRequiresAValidatedMediaReferenceAndDuration() {
        String mediaId = java.util.UUID.randomUUID().toString();
        assertThatCode(() -> service.validate("READ_ALOUD_V1", "{\"mediaPublicId\":\""
                + mediaId + "\",\"durationSeconds\":12}", ResponseConfidence.HIGH))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> service.validate("READ_ALOUD_V1",
                "{\"mediaPublicId\":\"" + mediaId + "\",\"durationSeconds\":121}",
                ResponseConfidence.HIGH))
                .hasFieldOrPropertyWithValue("code", "PRACTICE_ANSWER_INVALID");
    }

    @Test
    void unsupportedMediaRendererIsNotAcceptedAsADraft() {
        assertThatThrownBy(() -> service.validateDraft("MC_LISTENING_SINGLE_V1", 1,
                "{\"selectedOption\":\"a\"}", null))
                .hasFieldOrPropertyWithValue("code", "PRACTICE_UNSUPPORTED_RUNTIME");
    }
}
