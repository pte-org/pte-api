package com.pte.itembank.internal.service;

import com.pte.itembank.domain.Question;
import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.itembank.internal.exception.QuestionDeletionException;
import com.pte.itembank.internal.exception.QuestionNotFoundException;
import com.pte.itembank.internal.repository.QuestionRepository;
import com.pte.media.MediaService;
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
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(QuestionDeletionService.class);

    private final QuestionRepository questions;
    private final AuditLogService audit;
    private final MediaService media;

    public QuestionDeletionService(QuestionRepository questions, AuditLogService audit, MediaService media) {
        this.questions = questions;
        this.audit = audit;
        this.media = media;
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
        if (question.getTenantId() != null) {
            throw new QuestionNotFoundException();
        }
        if (question.isDeleted()) return;
        if (!QuestionLifecyclePolicy.canDeleteDraft(question) || questions.existsBySupersedesPublicId(publicId)) {
            throw new QuestionDeletionException();
        }
        question.setDeleted(true);
        question.setCurrent(false);
        audit.record(caller, ItembankConstants.QUESTION_AGGREGATE, publicId.toString(),
                ItembankConstants.QUESTION_DRAFT_DELETED, ItembankConstants.QUESTION_DRAFT_DELETED_SUMMARY);
        removeGeneratedMediaAfterCommit(question, caller);
    }

    /**
     * An AI-generated draft owns the audio or image that was generated for it. Once the deletion is committed the
     * file is removed too (unless another live question uses it), so deleted drafts do not keep using storage.
     * A failure here never undoes the deletion.
     */
    private void removeGeneratedMediaAfterCommit(Question question, CurrentUser caller) {
        if (question.getGenerationMeta() == null) {
            return;
        }
        java.util.List<UUID> files = new java.util.ArrayList<>();
        if (question.getAudioPromptRef() != null && !questions
                .existsByAudioPromptRefAndDeletedFalseAndPublicIdNot(question.getAudioPromptRef(),
                        question.getPublicId())) {
            files.add(question.getAudioPromptRef());
        }
        if (question.getImagePromptRef() != null && !questions
                .existsByImagePromptRefAndDeletedFalseAndPublicIdNot(question.getImagePromptRef(),
                        question.getPublicId())) {
            files.add(question.getImagePromptRef());
        }
        if (files.isEmpty()) {
            return;
        }
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        for (UUID file : files) {
                            try {
                                media.deleteGenerated(file, caller);
                            } catch (RuntimeException failure) {
                                LOG.warn("Generated media was not removed: errorType={}",
                                        failure.getClass().getSimpleName());
                            }
                        }
                    }
                });
    }
}
