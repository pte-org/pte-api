package com.pte.itembank.internal.generation.voice;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.enums.QuestionStatus;
import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.itembank.internal.exception.InvalidQuestionStatusTransitionException;
import com.pte.itembank.internal.exception.QuestionVersionConflictException;
import com.pte.itembank.internal.service.ItembankAccessPolicy;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.constant.SharedConstants;
import com.pte.shared.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Who may change a draft question and in which state: the same decision as editing it (the shared access policy,
 * a DRAFT status), plus a version the person actually saw. Asked again when the change is saved, because speaking
 * takes long enough for the question to move on.
 */
@Component
public class DraftWriteGuard {

    private final ItembankAccessPolicy accessPolicy;
    private final AuditLogService auditLogService;

    public DraftWriteGuard(ItembankAccessPolicy accessPolicy, AuditLogService auditLogService) {
        this.accessPolicy = accessPolicy;
        this.auditLogService = auditLogService;
    }

    public void require(Question question, CurrentUser caller, long expectedVersion) {
        if (!accessPolicy.canModifyDraft(caller, question.getAuthorUserPublicId())) {
            if (caller != null) {
                auditLogService.recordFailure(caller, ItembankConstants.QUESTION_AGGREGATE,
                        String.valueOf(question.getPublicId()), SharedConstants.AUDIT_AUTHORIZATION_DENIED,
                        ItembankConstants.ACADEMIC_DRAFT_WRITE_REQUIRED);
            }
            throw new AccessDeniedException(ItembankConstants.ACADEMIC_DRAFT_WRITE_REQUIRED);
        }
        if (question.getStatus() != QuestionStatus.DRAFT) {
            throw new InvalidQuestionStatusTransitionException();
        }
        if (question.getVersion() != expectedVersion) {
            throw new QuestionVersionConflictException();
        }
    }
}
