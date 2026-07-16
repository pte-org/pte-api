package com.aptis.modules.questionbank.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

@DisplayName("UpdateQuestionRequest Validation Tests")
class UpdateQuestionRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("valid update request with pteTaskType passes validation")
    void validUpdateWithPteTaskType() {
        UpdateQuestionRequest request = new UpdateQuestionRequest(
                "READING",
                1,
                "MULTIPLE_CHOICE",
                "Updated content",
                "instruction",
                1.0f,
                "explanation",
                null,
                null,
                null,
                List.of(),
                "B1",
                List.of(),
                List.of("A", "B", "C"),
                List.of("A"),
                "READING_FILL_IN_THE_BLANKS",
                null,
                null,
                null
        );

        Set<ConstraintViolation<UpdateQuestionRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("invalid pteTaskType in update fails validation")
    void invalidPteTaskTypeInUpdate() {
        UpdateQuestionRequest request = new UpdateQuestionRequest(
                "READING",
                1,
                "MULTIPLE_CHOICE",
                "Updated content",
                "instruction",
                1.0f,
                "explanation",
                null,
                null,
                null,
                List.of(),
                "B1",
                List.of(),
                List.of("A", "B", "C"),
                List.of("A"),
                "GARBAGE_TYPE",
                null,
                null,
                null
        );

        Set<ConstraintViolation<UpdateQuestionRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("pteTaskType"));
    }

    @Test
    @DisplayName("update can clear pteTaskType by setting null")
    void updateClearPteTaskType() {
        UpdateQuestionRequest request = new UpdateQuestionRequest(
                "READING",
                1,
                "MULTIPLE_CHOICE",
                "Updated content",
                "instruction",
                1.0f,
                "explanation",
                null,
                null,
                null,
                List.of(),
                "B1",
                List.of(),
                List.of("A", "B", "C"),
                List.of("A"),
                null,
                null,
                null,
                null
        );

        Set<ConstraintViolation<UpdateQuestionRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("update can set referenceAnswerText")
    void updateSetReferenceAnswerText() {
        UpdateQuestionRequest request = new UpdateQuestionRequest(
                "WRITING",
                1,
                "AUDIO_RECORD",
                "Updated essay prompt",
                "instruction",
                1.0f,
                "explanation",
                null,
                null,
                null,
                List.of(),
                "B1",
                List.of(),
                null,
                null,
                "WRITE_ESSAY",
                "Updated model answer",
                null,
                null
        );

        Set<ConstraintViolation<UpdateQuestionRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
        assertThat(request.referenceAnswerText()).isEqualTo("Updated model answer");
    }

    @Test
    @DisplayName("update can set word counts")
    void updateSetWordCounts() {
        UpdateQuestionRequest request = new UpdateQuestionRequest(
                "WRITING",
                1,
                "AUDIO_RECORD",
                "Retell the lecture",
                "instruction",
                1.0f,
                "explanation",
                null,
                null,
                null,
                List.of(),
                "B1",
                List.of(),
                null,
                null,
                "RETELL_LECTURE",
                "Sample lecture content",
                150,
                400
        );

        Set<ConstraintViolation<UpdateQuestionRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
        assertThat(request.minWordCount()).isEqualTo(150);
        assertThat(request.maxWordCount()).isEqualTo(400);
    }

    @Test
    @DisplayName("update supports all new fields together")
    void updateAllNewFieldsTogether() {
        UpdateQuestionRequest request = new UpdateQuestionRequest(
                "SPEAKING",
                1,
                "AUDIO_RECORD",
                "Answer the question",
                "instruction",
                1.0f,
                "explanation",
                30,
                10,
                3,
                List.of(),
                "B2",
                List.of("speaking", "accuracy"),
                null,
                null,
                "ANSWER_SHORT_QUESTION",
                "Expected answer outline",
                50,
                100
        );

        Set<ConstraintViolation<UpdateQuestionRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

}
