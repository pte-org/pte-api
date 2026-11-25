package com.pte.session.internal.exception.staff;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** A proctor can only be added before the exam starts; once it is open or closed the staff list is fixed. */
public class ProctorAssignmentLockedException extends DomainException {

    public ProctorAssignmentLockedException() {
        super(HttpStatus.CONFLICT, SessionConstants.PROCTOR_ASSIGNMENT_LOCKED, null,
                SessionConstants.PROCTOR_ASSIGNMENT_LOCKED_FRIENDLY);
    }
}
