package com.pte.itembank.internal.service;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.enums.QuestionStatus;

/** Authoritative local lifecycle rules; assessment can only freeze APPROVED questions. */
public final class QuestionLifecyclePolicy {
    private QuestionLifecyclePolicy() { }

    public static boolean canDeleteDraft(Question question) {
        return !question.isDeleted() && question.getStatus() == QuestionStatus.DRAFT
                && Boolean.FALSE.equals(question.getEverPublished())
                && question.getSupersedesPublicId() == null && question.getRevisionNumber() == 1;
    }

    public static boolean canArchive(Question question) {
        return !question.isDeleted() && (question.getStatus() == QuestionStatus.APPROVED
                || question.getStatus() == QuestionStatus.DRAFT && Boolean.TRUE.equals(question.getEverPublished()));
    }
}
