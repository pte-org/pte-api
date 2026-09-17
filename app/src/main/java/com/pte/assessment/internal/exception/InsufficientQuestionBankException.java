package com.pte.assessment.internal.exception;

import com.pte.assessment.internal.constant.AssessmentConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * The question bank doesn't have enough PUBLISHED+SHARED stock to generate an
 * exam for every requested task type. Carries every shortfall found — not
 * just the first — so the host sees the whole picture in one response.
 */
public class InsufficientQuestionBankException extends DomainException {

    private final List<Shortage> shortages;

    public InsufficientQuestionBankException(List<Shortage> shortages) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, AssessmentConstants.INSUFFICIENT_QUESTION_BANK, List.copyOf(shortages));
        this.shortages = List.copyOf(shortages);
    }

    public List<Shortage> getShortages() {
        return shortages;
    }

    public record Shortage(String taskType, int required, int available) {
    }
}
