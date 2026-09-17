package com.pte.itembank.internal.mapper;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.QuestionOption;
import com.pte.itembank.dto.response.OptionResponse;
import com.pte.itembank.dto.response.QuestionResponse;

import java.util.List;

/** Maps {@link Question} to its response DTO. */
public final class QuestionMapper {

    private QuestionMapper() {
    }

    public static QuestionResponse toResponse(Question question) {
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
                options);
    }

    private static OptionResponse toOption(QuestionOption option) {
        return new OptionResponse(option.getPublicId(), option.getText(), option.isCorrect(), option.getOrderIndex());
    }
}
