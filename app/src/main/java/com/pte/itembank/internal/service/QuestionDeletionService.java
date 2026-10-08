package com.pte.itembank.internal.service;

import com.pte.itembank.domain.Question;
import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.itembank.internal.exception.QuestionDeletionException;
import com.pte.itembank.internal.exception.QuestionNotFoundException;
import com.pte.itembank.internal.repository.QuestionRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.constant.SharedConstants;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityPolicy;
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
        // Reject callers that can never delete an academic draft before
        // looking up the resource. This preserves the deny-by-default
        // boundary and avoids leaking whether an arbitrary id exists.
        if (!SecurityPolicy.canModifyAcademicDraft(caller, null)) {
            throw new AccessDeniedException(SharedConstants.ACCESS_DENIED);
        }
        Question question = questions.findByPublicIdForUpdate(publicId).orElseThrow(QuestionNotFoundException::new);
        if (!SecurityPolicy.canModifyAcademicDraft(caller, question.getAuthorUserPublicId())) {
            if (caller != null) {
                audit.recordFailure(caller, ItembankConstants.QUESTION_AGGREGATE, publicId.toString(),
                        SharedConstants.AUDIT_AUTHORIZATION_DENIED,
                        ItembankConstants.ACADEMIC_DRAFT_WRITE_REQUIRED);
            }
            throw new AccessDeniedException(ItembankConstants.ACADEMIC_DRAFT_WRITE_REQUIRED);
        }
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
