package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

public class ExamAudienceReconciliationException extends DomainException {

    public ExamAudienceReconciliationException(List<UUID> unresolvedStudentPublicIds) {
        super(HttpStatus.CONFLICT, SessionConstants.EXAM_AUDIENCE_RECONCILIATION_FAILED,
                List.copyOf(unresolvedStudentPublicIds),
                SessionConstants.EXAM_AUDIENCE_RECONCILIATION_FAILED_FRIENDLY);
    }
}
