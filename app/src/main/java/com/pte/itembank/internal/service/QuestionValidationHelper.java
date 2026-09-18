package com.pte.itembank.internal.service;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.QuestionOption;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.itembank.internal.exception.QuestionValidationException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * Task-type-aware required-field validation. Category-driven off {@link PteTaskType}
 * flags rather than 22 hand-written rules, so a new task type is validated by
 * declaring its requirement flags.
 */
@Component
public class QuestionValidationHelper {

    public void validate(Question question) {
        PteTaskType type = question.getPteTaskType();

        if (!StringUtils.hasText(question.getTitle())) {
            throw new QuestionValidationException(ItembankConstants.TITLE_REQUIRED);
        }
        if (question.getMinWordCount() != null && question.getMaxWordCount() != null
                && question.getMinWordCount() > question.getMaxWordCount()) {
            throw new QuestionValidationException(ItembankConstants.INVALID_QUESTION_FIELDS);
        }

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
            Set<Integer> orderIndexes = new HashSet<>();
            for (QuestionOption option : question.getOptions()) {
                if (option.getOrderIndex() < 0 || !orderIndexes.add(option.getOrderIndex())
                        || (option.getBlankIndex() != null && option.getBlankIndex() < 0)
                        || (option.getCorrectGapIndex() != null && option.getCorrectGapIndex() < 0)
                        || (option.getBlankIndex() != null && option.getCorrectGapIndex() != null)) {
                    throw new QuestionValidationException(ItembankConstants.INVALID_QUESTION_FIELDS);
                }
            }
            if (type.requiresCorrectAnswer()
                    && question.getOptions().stream().noneMatch(QuestionOption::isCorrect)) {
                throw new QuestionValidationException(ItembankConstants.CORRECT_OPTION_REQUIRED);
            }
            if ((type == PteTaskType.MC_READING_SINGLE || type == PteTaskType.MC_LISTENING_SINGLE)
                    && question.getOptions().stream().filter(QuestionOption::isCorrect).count() != 1) {
                throw new QuestionValidationException(ItembankConstants.INVALID_QUESTION_FIELDS);
            }
        } else if (type.requiresCorrectAnswer() && !StringUtils.hasText(question.getCorrectAnswerText())) {
            throw new QuestionValidationException(ItembankConstants.CORRECT_ANSWER_REQUIRED);
        }
    }
}
