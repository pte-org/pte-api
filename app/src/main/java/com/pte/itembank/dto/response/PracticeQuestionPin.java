package com.pte.itembank.dto.response;

/**
 * Everything practice freezes into a session item for one question: the student-safe {@code content} and the
 * restricted {@code evaluation} material (answer keys), fetched together so a session's items load in one batch.
 */
public record PracticeQuestionPin(PracticeQuestionContent content, PracticeQuestionEvaluationContent evaluation) {
}
