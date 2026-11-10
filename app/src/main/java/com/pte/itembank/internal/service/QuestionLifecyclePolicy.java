package com.pte.itembank.internal.service;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.enums.QuestionStatus;

/** Authoritative local lifecycle rules; assessment can only freeze APPROVED questions. */
public final class QuestionLifecyclePolicy {
    private QuestionLifecyclePolicy() { }

    /**
     * A draft that was never published can go, including a revision of a published question: the
     * published source and the exam snapshots made from it are untouched by the removal.
     */
    public static boolean canDeleteDraft(Question question) {
        return !question.isDeleted() && question.getStatus() == QuestionStatus.DRAFT
                && Boolean.FALSE.equals(question.getEverPublished());
    }

    public static boolean canArchive(Question question) {
        return !question.isDeleted() && (question.getStatus() == QuestionStatus.APPROVED
                || question.getStatus() == QuestionStatus.DRAFT && Boolean.TRUE.equals(question.getEverPublished()));
    }
}
