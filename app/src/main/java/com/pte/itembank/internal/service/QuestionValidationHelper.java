package com.pte.itembank.internal.service;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.QuestionOption;
import com.pte.itembank.domain.QuestionTypeDefinition;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.itembank.internal.exception.QuestionValidationException;
import com.pte.itembank.QuestionTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * Task-type-aware required-field validation. In production the requirements are
 * loaded from the persisted question-type catalog; the enum flags are retained
 * only as a compatibility fallback for focused unit tests that construct this
 * helper without Spring.
 */
@Component
public class QuestionValidationHelper {

    private final QuestionTypeService questionTypeService;

    @Autowired
    public QuestionValidationHelper(QuestionTypeService questionTypeService) {
        this.questionTypeService = questionTypeService;
    }

    /** Compatibility constructor for focused tests without the catalog bean. */
    public QuestionValidationHelper() {
        this.questionTypeService = null;
    }

    public void validate(Question question) {
        PteTaskType type = question.getPteTaskType();
        QuestionTypeDefinition definition = definitionFor(question);
        boolean requiresAudioPrompt = definition == null ? type != null && type.requiresAudioPrompt() : definition.isRequiresAudioPrompt();
        boolean requiresImagePrompt = definition == null ? type != null && type.requiresImagePrompt() : definition.isRequiresImagePrompt();
        boolean requiresPromptText = definition == null ? type != null && type.requiresPromptText() : definition.isRequiresPromptText();
        boolean requiresWordCount = definition == null ? type != null && type.requiresWordCount() : definition.isRequiresWordCount();

        if (!StringUtils.hasText(question.getTitle())) {
            throw new QuestionValidationException(ItembankConstants.TITLE_REQUIRED);
        }
        if (question.getMinWordCount() != null && question.getMaxWordCount() != null
                && question.getMinWordCount() > question.getMaxWordCount()) {
            throw new QuestionValidationException(ItembankConstants.INVALID_QUESTION_FIELDS);
        }

        if (requiresAudioPrompt && question.getAudioPromptRef() == null) {
            throw new QuestionValidationException(ItembankConstants.AUDIO_PROMPT_REQUIRED);
        }
        if (requiresImagePrompt && question.getImagePromptRef() == null) {
            throw new QuestionValidationException(ItembankConstants.IMAGE_PROMPT_REQUIRED);
        }
        if (requiresPromptText && !StringUtils.hasText(question.getPromptText())) {
            throw new QuestionValidationException(ItembankConstants.PROMPT_TEXT_REQUIRED);
        }
        if (requiresWordCount && (question.getMinWordCount() == null || question.getMaxWordCount() == null)) {
            throw new QuestionValidationException(ItembankConstants.WORD_COUNT_REQUIRED);
        }
        validateAnswers(question, type, definition);
    }

    private void validateAnswers(Question question, PteTaskType type, QuestionTypeDefinition definition) {
        boolean requiresOptions = definition == null ? type != null && type.requiresOptions() : definition.isRequiresOptions();
        boolean requiresCorrectAnswer = definition == null ? type != null && type.requiresCorrectAnswer() : definition.isRequiresCorrectAnswer();
        boolean requiresSingleCorrectOption = definition == null
                ? type == PteTaskType.MC_READING_SINGLE || type == PteTaskType.MC_LISTENING_SINGLE
                : definition.isRequiresSingleCorrectOption();

        if (requiresOptions) {
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
            if (requiresCorrectAnswer
                    && question.getOptions().stream().noneMatch(QuestionOption::isCorrect)) {
                throw new QuestionValidationException(ItembankConstants.CORRECT_OPTION_REQUIRED);
            }
            if (requiresSingleCorrectOption
                    && question.getOptions().stream().filter(QuestionOption::isCorrect).count() != 1) {
                throw new QuestionValidationException(ItembankConstants.INVALID_QUESTION_FIELDS);
            }
        } else if (requiresCorrectAnswer && !StringUtils.hasText(question.getCorrectAnswerText())) {
            throw new QuestionValidationException(ItembankConstants.CORRECT_ANSWER_REQUIRED);
        }
    }

    private QuestionTypeDefinition definitionFor(Question question) {
        PteTaskType type = question.getPteTaskType();
        if (questionTypeService == null) {
            if (type == null) throw new QuestionValidationException(ItembankConstants.UNKNOWN_TASK_TYPE);
            return null;
        }
        String key = question.getTaskTypeKey() == null && type != null ? type.name() : question.getTaskTypeKey();
        if (key == null) {
            throw new QuestionValidationException(ItembankConstants.UNKNOWN_TASK_TYPE);
        }
        return questionTypeService.findDefinitionByCode(key)
                .orElseThrow(() -> new QuestionValidationException(ItembankConstants.UNKNOWN_TASK_TYPE));
    }
}
