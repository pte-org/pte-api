package com.pte.scoring.internal.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class StaleExaminerAssignmentPreviewException extends DomainException {

    public StaleExaminerAssignmentPreviewException() {
        super(HttpStatus.CONFLICT, "EXAMINER_ASSIGNMENT_PREVIEW_STALE", null,
                "The candidate pool changed after preview. Create a new preview before confirming.");
    }
}
