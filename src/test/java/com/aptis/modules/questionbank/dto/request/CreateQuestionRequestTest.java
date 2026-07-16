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

@DisplayName("CreateQuestionRequest Validation Tests")
class CreateQuestionRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("valid request with pteTaskType READING_FILL_IN_THE_BLANKS passes validation")
    void validPteTaskTypeReading() {
        CreateQuestionRequest request = new CreateQuestionRequest(
                "READING",
                1,
                "MULTIPLE_CHOICE",
                "What is the meaning of this word?",
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

        Set<ConstraintViolation<CreateQuestionRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("invalid pteTaskType string fails validation")
    void invalidPteTaskTypeString() {
        CreateQuestionRequest request = new CreateQuestionRequest(
                "READING",
                1,
                "MULTIPLE_CHOICE",
                "What is the meaning of this word?",
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
                "NOT_A_REAL_TYPE",
                null,
                null,
                null
        );

        Set<ConstraintViolation<CreateQuestionRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("pteTaskType"));
    }

    @Test
    @DisplayName("pteTaskType can be null (optional field)")
    void pteTaskTypeOptional() {
        CreateQuestionRequest request = new CreateQuestionRequest(
                "READING",
                1,
                "MULTIPLE_CHOICE",
                "What is the meaning of this word?",
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

        Set<ConstraintViolation<CreateQuestionRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("referenceAnswerText can be set and validated")
    void referenceAnswerTextSet() {
        CreateQuestionRequest request = new CreateQuestionRequest(
                "WRITING",
                1,
                "AUDIO_RECORD",
                "Summarize the text",
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
                "SUMMARIZE_WRITTEN_TEXT",
                "Model answer text here",
                null,
                null
        );

        Set<ConstraintViolation<CreateQuestionRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
        assertThat(request.referenceAnswerText()).isEqualTo("Model answer text here");
    }

    @Test
    @DisplayName("minWordCount and maxWordCount can be set")
    void wordCountsSet() {
        CreateQuestionRequest request = new CreateQuestionRequest(
                "WRITING",
                1,
                "AUDIO_RECORD",
                "Write an essay",
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
                "Model essay",
                100,
                500
        );

        Set<ConstraintViolation<CreateQuestionRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
        assertThat(request.minWordCount()).isEqualTo(100);
        assertThat(request.maxWordCount()).isEqualTo(500);
    }

    @Test
    @DisplayName("valid AI_SCORED task type passes")
    void validAiScoredType() {
        CreateQuestionRequest request = new CreateQuestionRequest(
                "SPEAKING",
                1,
                "AUDIO_RECORD",
                "Describe the image",
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
                "DESCRIBE_IMAGE",
                "Model description",
                null,
                null
        );

        Set<ConstraintViolation<CreateQuestionRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("valid OBJECTIVE task type passes")
    void validObjectiveType() {
        CreateQuestionRequest request = new CreateQuestionRequest(
                "READING",
                1,
                "MULTIPLE_CHOICE",
                "Which is correct?",
                "instruction",
                1.0f,
                "explanation",
                null,
                null,
                null,
                List.of(),
                "B1",
                List.of(),
                List.of("Option A", "Option B"),
                List.of("Option A"),
                "MULTIPLE_CHOICE_READING_SINGLE_ANSWER",
                null,
                null,
                null
        );

        Set<ConstraintViolation<CreateQuestionRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("valid PERSONAL_INTRODUCTION (UNSCORED) passes")
    void validUnscoredType() {
        CreateQuestionRequest request = new CreateQuestionRequest(
                "SPEAKING",
                1,
                "AUDIO_RECORD",
                "Introduce yourself",
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
                "PERSONAL_INTRODUCTION",
                null,
                null,
                null
        );

        Set<ConstraintViolation<CreateQuestionRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

}
