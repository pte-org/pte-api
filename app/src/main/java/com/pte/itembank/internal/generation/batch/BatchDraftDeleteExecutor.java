package com.pte.itembank.internal.generation.batch;

import com.pte.itembank.internal.service.QuestionDeletionService;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * One draft deletion in its own transaction. A separate bean so the REQUIRES_NEW proxy applies (no self-invocation):
 * a failing question rolls back alone and never undoes the others of the bulk call.
 */
@Service
public class BatchDraftDeleteExecutor {

    private final QuestionDeletionService deletion;

    public BatchDraftDeleteExecutor(QuestionDeletionService deletion) {
        this.deletion = deletion;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void delete(UUID questionPublicId, CurrentUser caller) {
        deletion.deleteDraft(questionPublicId, caller);
    }
}
