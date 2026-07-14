package com.aptis.modules.questionbank.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.aptis.modules.questionbank.domain.DifficultyLevel;
import com.aptis.modules.questionbank.domain.Question;
import com.aptis.modules.questionbank.domain.QuestionStatus;
import com.aptis.modules.questionbank.domain.QuestionType;
import com.aptis.modules.questionbank.domain.Skill;
import com.aptis.modules.asset.dto.response.AssetResponse;

public record QuestionResponse(
        UUID id,
        Integer version,
        Long parentId,
        Boolean isCurrent,
        Boolean isImmutable,
        Skill skill,
        Integer part,
        QuestionType questionType,
        String content,
        String instruction,
        Float scoreWeight,
        String explanation,
        Integer timeLimit,
        Integer prepTime,
        Integer maxPlayCount,
        List<AssetResponse> assets,
        DifficultyLevel difficultyLevel,
        List<String> topicTags,
        QuestionStatus status,
        UUID createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<String> options,
        List<String> correctAnswers
) {

    public static QuestionResponse from(Question question, List<AssetResponse> assets) {
        return new QuestionResponse(
                question.getPublicId(),
                question.getVersion(),
                question.getParentId(),
                question.getIsCurrent(),
                question.getIsImmutable(),
                question.getSkill(),
                question.getPart(),
                question.getQuestionType(),
                question.getContent(),
                question.getInstruction(),
                question.getScoreWeight(),
                question.getExplanation(),
                question.getTimeLimit(),
                question.getPrepTime(),
                question.getMaxPlayCount(),
                assets,
                question.getDifficultyLevel(),
                question.getTopicTags(),
                question.getStatus(),
                question.getCreatedBy(),
                question.getCreatedAt(),
                question.getUpdatedAt(),
                question.getOptions(),
                question.getCorrectAnswers());
    }
}
