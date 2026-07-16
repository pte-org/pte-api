package com.aptis.modules.questionbank.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

public record CreateQuestionRequest(

        @NotNull(message = "Skill is required")
        @Pattern(regexp = "GRAMMAR|VOCABULARY|LISTENING|READING|WRITING|SPEAKING",
                message = "Skill must be one of: GRAMMAR, VOCABULARY, LISTENING, READING, WRITING, SPEAKING")
        String skill,

        @NotNull(message = "Part is required")
        Integer part,

        @NotNull(message = "Question type is required")
        @Pattern(regexp = "MULTIPLE_CHOICE|FILL_IN_BLANK|MATCHING|TEXT_INPUT|AUDIO_RECORD|ORDERING",
                message = "Invalid question type")
        String questionType,

        @NotBlank(message = "Content is required")
        String content,

        String instruction,

        Float scoreWeight,

        String explanation,

        Integer timeLimit,

        Integer prepTime,

        Integer maxPlayCount,

        List<UUID> assetIds,


        @NotNull(message = "Difficulty level is required")
        @Pattern(regexp = "A1|A2|B1|B2|C1",
                message = "Difficulty level must be one of: A1, A2, B1, B2, C1")
        String difficultyLevel,

        List<String> topicTags,

        List<String> options,

        List<String> correctAnswers,

        @Pattern(regexp = "PERSONAL_INTRODUCTION|READ_ALOUD|REPEAT_SENTENCE|DESCRIBE_IMAGE|RETELL_LECTURE"
                + "|ANSWER_SHORT_QUESTION|RESPOND_TO_A_SITUATION|SUMMARIZE_GROUP_DISCUSSION"
                + "|SUMMARIZE_WRITTEN_TEXT|WRITE_ESSAY|READING_FILL_IN_THE_BLANKS"
                + "|READING_WRITING_FILL_IN_THE_BLANKS|MULTIPLE_CHOICE_READING_SINGLE_ANSWER"
                + "|MULTIPLE_CHOICE_READING_MULTIPLE_ANSWER|RE_ORDER_PARAGRAPHS|SUMMARIZE_SPOKEN_TEXT"
                + "|MULTIPLE_CHOICE_LISTENING_SINGLE_ANSWER|MULTIPLE_CHOICE_LISTENING_MULTIPLE_ANSWER"
                + "|LISTENING_FILL_IN_THE_BLANKS|HIGHLIGHT_CORRECT_SUMMARY|SELECT_MISSING_WORD"
                + "|HIGHLIGHT_INCORRECT_WORDS|WRITE_FROM_DICTATION",
                message = "Invalid PTE task type")
        String pteTaskType,

        String referenceAnswerText,

        Integer minWordCount,

        Integer maxWordCount
) {
}
