package com.pte.itembank.internal.service.lifecycle;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.QuestionOption;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Business snapshot of a question for the audit trail: the content and answer key a reviewer must be able to
 * compare. Media are kept as references only, and AI provenance is left out (it is not authored content).
 */
record QuestionAuditState(
        String taskTypeKey,
        String cefrLevel,
        String promptText,
        UUID audioPromptRef,
        UUID imagePromptRef,
        String referenceAnswerText,
        String correctAnswerText,
        Integer minWordCount,
        Integer maxWordCount,
        String answerHintVi,
        String answerHintEn,
        boolean availableForExam,
        boolean availableForPractice,
        List<OptionState> options) {

    record OptionState(int orderIndex, Integer blankIndex, String text, boolean correct, Integer correctGapIndex) {
    }

    private static final Comparator<OptionState> OPTION_ORDER = Comparator.comparingInt(OptionState::orderIndex)
            .thenComparing(OptionState::blankIndex, Comparator.nullsFirst(Comparator.naturalOrder()));

    static QuestionAuditState of(Question question) {
        return new QuestionAuditState(
                question.getTaskTypeKey(),
                question.getCefrLevel() == null ? null : question.getCefrLevel().name(),
                question.getPromptText(),
                question.getAudioPromptRef(),
                question.getImagePromptRef(),
                question.getReferenceAnswerText(),
                question.getCorrectAnswerText(),
                question.getMinWordCount(),
                question.getMaxWordCount(),
                question.getAnswerHintVi(),
                question.getAnswerHintEn(),
                question.isAvailableForExam(),
                question.isAvailableForPractice(),
                question.getOptions().stream().map(QuestionAuditState::optionOf).sorted(OPTION_ORDER).toList());
    }

    private static OptionState optionOf(QuestionOption option) {
        return new OptionState(option.getOrderIndex(), option.getBlankIndex(), option.getText(), option.isCorrect(),
                option.getCorrectGapIndex());
    }
}
