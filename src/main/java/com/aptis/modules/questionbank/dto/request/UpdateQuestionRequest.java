package com.aptis.modules.questionbank.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

public record UpdateQuestionRequest(

        @NotNull(message = "Skill is required")
        @Pattern(regexp = "GRAMMAR|VOCABULARY|LISTENING|READING|WRITING|SPEAKING",
                message = "Skill must be one of: GRAMMAR, VOCABULARY, LISTENING, READING, WRITING, SPEAKING")
        String skill,

        @NotNull(message = "Part is required")
        Integer part,

        @NotNull(message = "Question type is required")
        @Pattern(regexp = "MULTIPLE_CHOICE|FILL_IN_BLANK|MATCHING|DRAG_DROP|TEXT_INPUT|AUDIO_RECORD",
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

        List<String> correctAnswers
) {
}
