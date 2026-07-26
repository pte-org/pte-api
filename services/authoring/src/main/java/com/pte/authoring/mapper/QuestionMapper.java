package com.pte.authoring.mapper;

import com.pte.authoring.domain.Question;
import com.pte.authoring.domain.QuestionOption;
import com.pte.authoring.dto.response.OptionResponse;
import com.pte.authoring.dto.response.QuestionResponse;

import java.util.List;

/** Maps {@link Question} to its response DTO. Skills are supplied by the service (config-driven). */
public final class QuestionMapper {

    private QuestionMapper() {
    }

    public static QuestionResponse toResponse(Question question, List<String> skills) {
        List<OptionResponse> options = question.getOptions().stream()
                .map(QuestionMapper::toOption)
                .toList();
        return new QuestionResponse(
                question.getPublicId(),
                question.getPteTaskType().name(),
                question.getPteTaskType().getSection().name(),
                question.getVisibility().name(),
                question.getTenantId(),
                question.getStatus().name(),
                question.getTitle(),
                question.getPromptText(),
                question.getAudioPromptRef(),
                question.getImagePromptRef(),
                question.getReferenceAnswerText(),
                question.getCorrectAnswerText(),
                question.getMinWordCount(),
                question.getMaxWordCount(),
                options,
                skills);
    }

    private static OptionResponse toOption(QuestionOption option) {
        return new OptionResponse(option.getPublicId(), option.getText(), option.isCorrect(), option.getOrderIndex());
    }
}
