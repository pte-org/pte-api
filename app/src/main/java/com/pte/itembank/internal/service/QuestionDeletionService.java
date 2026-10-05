package com.pte.itembank.internal.service;

import com.pte.itembank.domain.Question;
import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.itembank.internal.exception.QuestionDeletionException;
import com.pte.itembank.internal.exception.QuestionNotFoundException;
import com.pte.itembank.internal.repository.QuestionRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.constant.SharedConstants;
import com.pte.shared.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** Removes only provably unused drafts, retaining their row, media and audit. */
@Service
public class QuestionDeletionService {
    private final QuestionRepository questions;
    private final AuditLogService audit;

    public QuestionDeletionService(QuestionRepository questions, AuditLogService audit) {
        this.questions = questions;
        this.audit = audit;
    }

    @Transactional
    public void deleteDraft(UUID publicId, CurrentUser caller) {
        if (!caller.isPlatformUser() || !(caller.hasRole("PLATFORM_ADMIN") || caller.hasRole("PLATFORM_AUTHOR"))) {
            throw new AccessDeniedException(SharedConstants.ACCESS_DENIED);
        }
        Question question = questions.findByPublicIdForUpdate(publicId).orElseThrow(QuestionNotFoundException::new);
        if (question.getTenantId() != null || question.getVisibility() != com.pte.itembank.domain.enums.Visibility.SHARED) {
            throw new QuestionNotFoundException();
        }
        if (question.isDeleted()) return;
        if (!QuestionLifecyclePolicy.canDeleteDraft(question)
                || questions.existsByRevisionGroupPublicIdAndPublicIdNot(question.getRevisionGroupPublicId(), publicId)) {
            throw new QuestionDeletionException();
        }
        question.setDeleted(true);
        question.setCurrent(false);
        audit.record(caller, ItembankConstants.QUESTION_AGGREGATE, publicId.toString(),
                ItembankConstants.QUESTION_DRAFT_DELETED, ItembankConstants.QUESTION_DRAFT_DELETED_SUMMARY);
    }
}
