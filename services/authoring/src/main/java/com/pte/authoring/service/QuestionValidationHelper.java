package com.pte.authoring.service;

import com.pte.authoring.constant.AuthoringConstants;
import com.pte.authoring.domain.Question;
import com.pte.authoring.domain.QuestionOption;
import com.pte.authoring.domain.enums.PteTaskType;
import com.pte.authoring.domain.exception.QuestionValidationException;
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
            throw new QuestionValidationException(AuthoringConstants.AUDIO_PROMPT_REQUIRED);
        }
        if (type.requiresImagePrompt() && question.getImagePromptRef() == null) {
            throw new QuestionValidationException(AuthoringConstants.IMAGE_PROMPT_REQUIRED);
        }
        if (type.requiresPromptText() && !StringUtils.hasText(question.getPromptText())) {
            throw new QuestionValidationException(AuthoringConstants.PROMPT_TEXT_REQUIRED);
        }
        if (type.requiresWordCount() && (question.getMinWordCount() == null || question.getMaxWordCount() == null)) {
            throw new QuestionValidationException(AuthoringConstants.WORD_COUNT_REQUIRED);
        }
        validateAnswers(question, type);
    }

    private void validateAnswers(Question question, PteTaskType type) {
        if (type.requiresOptions()) {
            if (question.getOptions().isEmpty()) {
                throw new QuestionValidationException(AuthoringConstants.OPTIONS_REQUIRED);
            }
            if (type.requiresCorrectAnswer()
                    && question.getOptions().stream().noneMatch(QuestionOption::isCorrect)) {
                throw new QuestionValidationException(AuthoringConstants.CORRECT_OPTION_REQUIRED);
            }
        } else if (type.requiresCorrectAnswer() && !StringUtils.hasText(question.getCorrectAnswerText())) {
            throw new QuestionValidationException(AuthoringConstants.CORRECT_ANSWER_REQUIRED);
        }
    }
}
