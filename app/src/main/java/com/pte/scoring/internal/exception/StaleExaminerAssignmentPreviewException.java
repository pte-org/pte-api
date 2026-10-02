package com.pte.scoring.internal.exception;

import com.pte.scoring.internal.constant.ExaminerAssignmentConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class StaleExaminerAssignmentPreviewException extends DomainException {

    public StaleExaminerAssignmentPreviewException() {
        super(HttpStatus.CONFLICT, ExaminerAssignmentConstants.STALE_PREVIEW, null,
                ExaminerAssignmentConstants.STALE_PREVIEW_MESSAGE);
    }
}
