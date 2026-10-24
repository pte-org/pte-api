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
    void unknownRendererIsNotAcceptedAsAnAnswer() {
        assertThatThrownBy(() -> service.validate("NOT_A_RENDERER_V1",
                "{\"text\":\"hello\"}", ResponseConfidence.HIGH))
                .hasFieldOrPropertyWithValue("code", "PRACTICE_UNSUPPORTED_RUNTIME");
    }

    @Test
    void everyExamRendererAcceptsItsOwnAnswerShape() {
        String mediaId = java.util.UUID.randomUUID().toString();
        String speech = "{\"mediaPublicId\":\"" + mediaId + "\",\"durationSeconds\":30}";
        java.util.Map<String, String> valid = java.util.Map.ofEntries(
                java.util.Map.entry("MC_LISTENING_SINGLE_V1", "{\"selectedOption\":\"1\"}"),
                java.util.Map.entry("MC_LISTENING_MULTIPLE_V1", "{\"selectedOptions\":[\"0\",\"2\"]}"),
                java.util.Map.entry("SELECT_MISSING_WORD_V1", "{\"selectedOption\":\"1\"}"),
                java.util.Map.entry("HIGHLIGHT_CORRECT_SUMMARY_V1", "{\"selectedOption\":\"2\"}"),
                java.util.Map.entry("SUMMARIZE_SPOKEN_TEXT_V1", "{\"text\":\"A summary.\"}"),
                java.util.Map.entry("WRITE_FROM_DICTATION_V1", "{\"text\":\"A sentence.\"}"),
                java.util.Map.entry("FILL_IN_THE_BLANKS_TYPE_IN_V1", "{\"blanks\":[\"a\",\"\",\"c\"]}"),
                java.util.Map.entry("HIGHLIGHT_INCORRECT_WORDS_V1", "{\"selectedWordIndexes\":[3,7]}"),
                java.util.Map.entry("REPEAT_SENTENCE_V1", speech),
                java.util.Map.entry("DESCRIBE_IMAGE_V1", speech),
                java.util.Map.entry("RE_TELL_LECTURE_V1", speech),
                java.util.Map.entry("ANSWER_SHORT_QUESTION_V1", speech),
                java.util.Map.entry("RESPOND_TO_A_SITUATION_V1", speech),
                java.util.Map.entry("SUMMARIZE_GROUP_DISCUSSION_V1", speech));
        valid.forEach((renderer, payload) -> assertThatCode(
                () -> service.validate(renderer, payload, ResponseConfidence.HIGH))
                .as(renderer).doesNotThrowAnyException());
    }

    @Test
    void newRenderersRejectForeignOrMalformedShapes() {
        assertThatThrownBy(() -> service.validate("FILL_IN_THE_BLANKS_TYPE_IN_V1",
                "{\"blanks\":[]}", ResponseConfidence.HIGH))
                .hasFieldOrPropertyWithValue("code", "PRACTICE_ANSWER_INVALID");
        assertThatThrownBy(() -> service.validate("FILL_IN_THE_BLANKS_TYPE_IN_V1",
                "{\"blanks\":[\"a\",1]}", ResponseConfidence.HIGH))
                .hasFieldOrPropertyWithValue("code", "PRACTICE_ANSWER_INVALID");
        assertThatThrownBy(() -> service.validate("HIGHLIGHT_INCORRECT_WORDS_V1",
                "{\"selectedWordIndexes\":[-1]}", ResponseConfidence.HIGH))
                .hasFieldOrPropertyWithValue("code", "PRACTICE_ANSWER_INVALID");
        assertThatThrownBy(() -> service.validate("HIGHLIGHT_INCORRECT_WORDS_V1",
                "{\"selectedWordIndexes\":[\"3\"]}", ResponseConfidence.HIGH))
                .hasFieldOrPropertyWithValue("code", "PRACTICE_ANSWER_INVALID");
        assertThatThrownBy(() -> service.validate("SUMMARIZE_SPOKEN_TEXT_V1",
                "{\"selectedOption\":\"a\"}", ResponseConfidence.HIGH))
                .hasFieldOrPropertyWithValue("code", "PRACTICE_ANSWER_INVALID");
        assertThatThrownBy(() -> service.validate("MC_LISTENING_SINGLE_V1",
                "{\"text\":\"hello\"}", ResponseConfidence.HIGH))
                .hasFieldOrPropertyWithValue("code", "PRACTICE_ANSWER_INVALID");
    }

    @Test
    void everySpeechRendererIsRecognisedAsARecording() {
        for (String renderer : new String[] {"READ_ALOUD_V1", "REPEAT_SENTENCE_V1", "DESCRIBE_IMAGE_V1",
                "RE_TELL_LECTURE_V1", "ANSWER_SHORT_QUESTION_V1", "RESPOND_TO_A_SITUATION_V1",
                "SUMMARIZE_GROUP_DISCUSSION_V1"}) {
            assertThatCode(() -> service.requireRecordedMediaPublicId(renderer,
                    "{\"mediaPublicId\":\"" + java.util.UUID.randomUUID() + "\",\"durationSeconds\":5}"))
                    .as(renderer).doesNotThrowAnyException();
        }
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
    void unknownRendererIsNotAcceptedAsADraft() {
        assertThatThrownBy(() -> service.validateDraft("NOT_A_RENDERER_V1", 1,
                "{\"selectedOption\":\"a\"}", null))
                .hasFieldOrPropertyWithValue("code", "PRACTICE_UNSUPPORTED_RUNTIME");
    }
}
