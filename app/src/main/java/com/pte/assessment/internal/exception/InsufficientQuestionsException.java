package com.pte.assessment.internal.exception;

import com.pte.assessment.internal.constant.AssessmentConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.stream.Collectors;

/** Raised before any blueprint/snapshot write when the approved bank is short. */
public class InsufficientQuestionsException extends DomainException {

    private final List<MissingQuestionSlot> missingSlots;

    public InsufficientQuestionsException(List<MissingQuestionSlot> missingSlots) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, formatMessage(missingSlots));
        this.missingSlots = List.copyOf(missingSlots);
    }

    public List<MissingQuestionSlot> getMissingSlots() {
        return missingSlots;
    }

    private static String formatMessage(List<MissingQuestionSlot> missingSlots) {
        String details = missingSlots.stream()
                .map(slot -> "{taskType=%s, required=%d, available=%d}"
                        .formatted(slot.taskType(), slot.required(), slot.available()))
                .collect(Collectors.joining(", "));
        return AssessmentConstants.INSUFFICIENT_QUESTIONS + ": [" + details + "]";
    }

    public record MissingQuestionSlot(String taskType, int required, long available) {
    }
}
