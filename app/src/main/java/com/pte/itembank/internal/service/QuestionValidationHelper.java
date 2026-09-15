package com.pte.itembank.internal.service;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.QuestionOption;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.itembank.internal.exception.QuestionValidationException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Task-type-aware required-field validation. Category-driven off {@link PteTaskType}
 * flags rather than 22 hand-written rules, so a new task type is validated by
 * declaring its requirement flags.
 */
@Component
public class QuestionValidationHelper {

    public void validate(Question question) {
        PteTaskType type = question.getPteTaskType();

        if (type.requiresAudioPrompt() && question.getAudioPromptRef() == null) {
            throw new QuestionValidationException(ItembankConstants.AUDIO_PROMPT_REQUIRED);
        }
        if (type.requiresImagePrompt() && question.getImagePromptRef() == null) {
            throw new QuestionValidationException(ItembankConstants.IMAGE_PROMPT_REQUIRED);
        }
        if (type.requiresPromptText() && !StringUtils.hasText(question.getPromptText())) {
            throw new QuestionValidationException(ItembankConstants.PROMPT_TEXT_REQUIRED);
        }
        if (type.requiresWordCount() && (question.getMinWordCount() == null || question.getMaxWordCount() == null)) {
            throw new QuestionValidationException(ItembankConstants.WORD_COUNT_REQUIRED);
        }
        validateAnswers(question, type);
    }

    private void validateAnswers(Question question, PteTaskType type) {
        if (type.requiresOptions()) {
            if (question.getOptions().isEmpty()) {
                throw new QuestionValidationException(ItembankConstants.OPTIONS_REQUIRED);
            }
            if (type.requiresCorrectAnswer()
                    && question.getOptions().stream().noneMatch(QuestionOption::isCorrect)) {
                throw new QuestionValidationException(ItembankConstants.CORRECT_OPTION_REQUIRED);
            }
        } else if (type.requiresCorrectAnswer() && !StringUtils.hasText(question.getCorrectAnswerText())) {
            throw new QuestionValidationException(ItembankConstants.CORRECT_ANSWER_REQUIRED);
        }
    }
}
